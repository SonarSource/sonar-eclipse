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

import java.util.Arrays;
import java.util.List;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.sonar.ide.eclipse.common.issues.ISonarIssueWithPath;
import org.sonar.ide.eclipse.core.internal.PreferencesUtils;
import org.sonar.ide.eclipse.core.internal.SonarCorePlugin;
import org.sonar.ide.eclipse.core.internal.markers.MarkerUtils;
import org.sonar.ide.eclipse.core.internal.markers.SonarMarker;
import org.sonar.ide.eclipse.core.internal.remote.EclipseSonar;
import org.sonar.ide.eclipse.core.internal.remote.SourceCode;
import org.sonar.ide.eclipse.core.internal.resources.ResourceUtils;
import org.sonar.ide.eclipse.core.internal.resources.SonarProject;
import org.sonar.ide.eclipse.wsclient.ConnectionException;

public class SynchronizeAllIssuesJob extends Job {

  private IProgressMonitor monitor;
  private List<AnalyzeProjectRequest> requests;

  public static void createAndSchedule(IProject project, boolean debugEnabled) {
    AnalyzeProjectRequest request = new AnalyzeProjectRequest(project)
      .setDebugEnabled(debugEnabled);
    new SynchronizeAllIssuesJob(Arrays.asList(request)).schedule();
  }

  public SynchronizeAllIssuesJob(List<AnalyzeProjectRequest> requests) {
    super("Synchronize issues");
    this.requests = requests;
    setPriority(Job.LONG);
  }

  @Override
  protected IStatus run(final IProgressMonitor monitor) {
    this.monitor = monitor;
    IStatus status;
    try {
      monitor.beginTask("Synchronize", requests.size());

      for (final AnalyzeProjectRequest request : requests) {
        if (monitor.isCanceled()) {
          break;
        }
        IProject project = request.getProject();
        EclipseSonar sonar = EclipseSonar.getInstance(project);
        // Is server reachable?
        if (project.isAccessible() && sonar != null) {
          MarkerUtils.deleteIssuesMarkers(project);
          monitor.subTask(project.getName());
          if (!PreferencesUtils.isForceFullPreview()) {
            // Only get remote issues in incremental mode
            fetchRemoteIssues(project, monitor);
          }
          scheduleAnalysis(request);
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

  private void scheduleAnalysis(AnalyzeProjectRequest request) throws InterruptedException {
    AnalyzeProjectJob analyzeProjectJob = new AnalyzeProjectJob(request);
    analyzeProjectJob.schedule();
    analyzeProjectJob.join();
  }

  public IProgressMonitor getMonitor() {
    return monitor;
  }

  private void fetchRemoteIssues(final IProject project, IProgressMonitor monitor) throws CoreException {
    long start = System.currentTimeMillis();
    SonarCorePlugin.getDefault().info("Retrieve remote issues of project " + project.getName() + "...\n");

    SonarProject sonarProject = SonarProject.getInstance(project);
    if (monitor.isCanceled()) {
      return;
    }
    EclipseSonar sonar = EclipseSonar.getInstance(project);
    if (sonar == null) {
      return;
    }
    SourceCode sourceCode = sonar.search(project);

    if (monitor.isCanceled()) {
      return;
    }

    if (sourceCode != null) {
      doRefreshIssues(sonarProject, sourceCode, monitor);
      sonarProject.setLastAnalysisDate(sourceCode.getAnalysisDate());
      sonarProject.save();
    } else {
      SonarCorePlugin.getDefault().error("Project not found on remote SonarQube server [" + sonarProject.getKey() + "]\n");
    }
    SonarCorePlugin.getDefault().debug("Done in " + (System.currentTimeMillis() - start) + "ms\n");
  }

  private void doRefreshIssues(SonarProject sonarProject, SourceCode sourceCode, IProgressMonitor monitor) throws CoreException {
    long start = System.currentTimeMillis();
    List<ISonarIssueWithPath> issues = sourceCode.getRemoteIssuesRecursively(monitor);
    SonarCorePlugin.getDefault().debug("  WS call took " + (System.currentTimeMillis() - start) + "ms for " + issues.size() + " issues\n");
    for (ISonarIssueWithPath issue : issues) {
      IResource eclipseResource = ResourceUtils.findResource(sonarProject, issue.resourceKey());
      if (eclipseResource instanceof IFile) {
        SonarMarker.create(eclipseResource, false, issue);
      }
      if (monitor.isCanceled()) {
        return;
      }
    }
  }
}
