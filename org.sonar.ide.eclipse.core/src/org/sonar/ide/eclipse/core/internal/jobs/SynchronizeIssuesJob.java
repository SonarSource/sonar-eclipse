/*
 * SonarQube Eclipse
 * Copyright (C) 2010-2015 SonarSource
 * sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.ide.eclipse.core.internal.jobs;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.sonar.ide.eclipse.common.issues.ISonarIssue;
import org.sonar.ide.eclipse.core.internal.SonarCorePlugin;
import org.sonar.ide.eclipse.core.internal.markers.MarkerUtils;
import org.sonar.ide.eclipse.core.internal.markers.SonarMarker;
import org.sonar.ide.eclipse.core.internal.remote.EclipseSonar;
import org.sonar.ide.eclipse.core.internal.remote.SourceCode;
import org.sonar.ide.eclipse.core.internal.resources.ResourceUtils;
import org.sonar.ide.eclipse.core.internal.resources.SonarProject;
import org.sonar.ide.eclipse.wsclient.ConnectionException;

/**
 * This class load issues in background.
 *
 */
public class SynchronizeIssuesJob extends Job implements IResourceProxyVisitor {

  public static final Object REMOTE_SONAR_JOB_FAMILY = new Object();

  private final List<? extends IResource> resources;
  private IProgressMonitor monitor;

  private boolean force;

  public SynchronizeIssuesJob(final List<? extends IResource> resources, boolean force) {
    super("Synchronize issues");
    this.force = force;
    setPriority(Job.LONG);
    this.resources = resources;
  }

  @Override
  protected IStatus run(final IProgressMonitor monitor) {
    this.monitor = monitor;
    IStatus status;
    try {
      monitor.beginTask("Synchronize", resources.size());

      for (final IResource resource : resources) {
        if (ResourceUtils.adapt(resource) == null) {
          break;
        }
        if (monitor.isCanceled()) {
          break;
        }
        if (resource.isAccessible() && !MarkerUtils.isResourceLocallyAnalysed(resource)) {
          monitor.subTask(resource.getName());
          resource.accept(this, IResource.NONE);
        }
        monitor.worked(1);
      }

      if (!monitor.isCanceled()) {
        status = Status.OK_STATUS;
      } else {
        status = Status.CANCEL_STATUS;
      }
    } catch (final ConnectionException e) {
      status = new Status(IStatus.ERROR, SonarCorePlugin.PLUGIN_ID, IStatus.ERROR, "Unable to contact SonarQube server", e);
    } catch (final Exception e) {
      status = new Status(IStatus.ERROR, SonarCorePlugin.PLUGIN_ID, IStatus.ERROR, e.getLocalizedMessage(), e);
    } finally {
      monitor.done();
    }
    return status;
  }

  public IProgressMonitor getMonitor() {
    return monitor;
  }

  @Override
  public boolean visit(final IResourceProxy proxy) throws CoreException {
    if (proxy.getType() == IResource.FILE) {
      IFile file = (IFile) proxy.requestResource();
      retrieveMarkers(file, getMonitor());
      // do not visit members of this resource
      return false;
    }
    return true;
  }

  private void retrieveMarkers(final IFile resource, final IProgressMonitor monitor) {
    if ((resource == null) || !resource.exists() || monitor.isCanceled()) {
      return;
    }
    SonarProject sonarProject = SonarProject.getInstance(resource.getProject());
    EclipseSonar eclipseSonar = EclipseSonar.getInstance(resource.getProject());
    if (eclipseSonar == null || !force && !MarkerUtils.needRefresh(resource, sonarProject, eclipseSonar.getSonarServer())) {
      return;
    }
    try {
      long start = System.currentTimeMillis();
      SonarCorePlugin.getDefault().debug("Retrieve issues of resource " + resource.getName() + "...\n");
      final Collection<ISonarIssue> issues = retrieveIssues(eclipseSonar, resource, monitor);
      SonarCorePlugin.getDefault().debug("Done in " + (System.currentTimeMillis() - start) + "ms\n");
      long startMarker = System.currentTimeMillis();
      SonarCorePlugin.getDefault().debug("Update markers on resource " + resource.getName() + "...\n");
      MarkerUtils.deleteIssuesMarkers(resource);
      for (final ISonarIssue issue : issues) {
        SonarMarker.create(resource, false, issue);
      }
      MarkerUtils.updatePersistentProperties(resource, sonarProject, eclipseSonar.getSonarServer());
      SonarCorePlugin.getDefault().debug("Done in " + (System.currentTimeMillis() - startMarker) + "ms\n");
    } catch (final Exception ex) {
      SonarCorePlugin.getDefault().error(ex.getMessage(), ex);
    }
  }

  private static Collection<ISonarIssue> retrieveIssues(EclipseSonar sonar, IResource resource, IProgressMonitor monitor) {
    SourceCode sourceCode = sonar.search(resource);
    if (sourceCode == null) {
      SonarCorePlugin.getDefault().debug("Unable to find remote resource " + resource.getName() + " on SonarQube server");
      return Collections.emptyList();
    }
    return sourceCode.getRemoteIssuesWithLineCorrection(monitor);
  }

  @Override
  public boolean belongsTo(Object family) {
    return family.equals(REMOTE_SONAR_JOB_FAMILY) ? true : super.belongsTo(family);
  }

}
