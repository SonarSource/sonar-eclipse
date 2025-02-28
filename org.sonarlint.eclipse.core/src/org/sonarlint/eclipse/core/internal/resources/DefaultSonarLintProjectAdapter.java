/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2025 SonarSource SA
 * sonarlint@sonarsource.com
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarlint.eclipse.core.internal.resources;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.Subscriber;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.backend.FileSystemSynchronizer;
import org.sonarlint.eclipse.core.internal.cache.DefaultSonarLintProjectAdapterCache;
import org.sonarlint.eclipse.core.internal.cache.IProjectScopeProviderCache;
import org.sonarlint.eclipse.core.internal.extension.SonarLintExtensionTracker;
import org.sonarlint.eclipse.core.internal.utils.SonarLintUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintFile;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;

public class DefaultSonarLintProjectAdapter implements ISonarLintProject {
  private static final String UNABLE_TO_ANALYZE_CHANGED_FILES = "Unable to collect changed files";

  private final IProject project;

  public DefaultSonarLintProjectAdapter(IProject project) {
    this.project = project;
  }

  @Override
  public String getName() {
    return project.getName();
  }

  @Override
  public Path getWorkingDir() {
    return project.getWorkingLocation(SonarLintCorePlugin.PLUGIN_ID).toFile().toPath();
  }

  @Override
  public boolean exists(String relativeFilePath) {
    return project.getFile(relativeFilePath).exists();
  }

  @Override
  public Optional<ISonarLintFile> find(String relativeFilePath) {
    var file = project.getFile(relativeFilePath);
    return file.exists() ? Optional.of(new DefaultSonarLintFileAdapter(this, file)) : Optional.empty();
  }

  @Override
  public Object getObjectToNotify() {
    return project;
  }

  @Override
  public Collection<ISonarLintFile> files() {
    var configScopeId = getConfigScopeId();

    var cachedFiles = DefaultSonarLintProjectAdapterCache.INSTANCE.getEntry(configScopeId);
    if (cachedFiles != null) {
      return cachedFiles;
    }

    var exclusions = getExclusions(configScopeId);
    var result = new ArrayList<ISonarLintFile>();
    try {
      project.accept(new IResourceVisitor() {
        @Override
        public boolean visit(IResource resource) throws CoreException {
          var fullPath = resource.getFullPath();

          // Immediately rule out files in the VCS and files related to Node.js "metadata" / storage or Python virtual
          // environments. We don't care for these ones no matter if removed, changed, or added!
          // INFO: We also exclude buggy Eclipse PDE files!
          if (SonarLintUtils.insideVCSFolder(fullPath)
            || SonarLintUtils.isNodeJsRelated(fullPath)
            || SonarLintUtils.isPythonRelated(fullPath)
            || SonarLintUtils.isIncorrectEclipsePDE(fullPath)) {
            return false;
          }

          // Compared to "FileSystemSynchronizer#visitDeltaPostChange" this is on all files and folders and not only
          // the delta. Therefore we can check for a folder whether its "whole" path is in there!
          if (exclusions.contains(fullPath)) {
            return false;
          }

          // We don't want to visit all the folders except the ".sonarlint" one due to it possibly containing shared
          // Connected Mode configuration files!
          if (resource.getType() == IResource.FOLDER
            && FileSystemSynchronizer.SONARLINT_FOLDER.equals(resource.getName())) {
            return true;
          }

          if (!SonarLintUtils.isSonarLintFileCandidate(resource)) {
            return false;
          }
          var sonarLintFile = SonarLintUtils.adapt(resource, ISonarLintFile.class,
            "[DefaultSonarLintProjectAdapter#files] Try get file of resource '" + resource + "'");
          if (sonarLintFile != null) {
            result.add(sonarLintFile);
          }
          return true;
        }
      });
    } catch (CoreException e) {
      SonarLintLogger.get().error("Error collecting files in project " + project.getName(), e);
    }

    DefaultSonarLintProjectAdapterCache.INSTANCE.putEntry(getConfigScopeId(), result);
    return result;
  }

  /**
   *  When the user has opted out of using Eclipse plug-ins for the indexing and exclusions, we don't call the
   *  extension points to get them and skip the whole process.
   *
   *  Otherwise, we try to load it from the cache first and if that is not possible, we access the extension points!
   *
   *  @param configScopeId used in the cache as a key
   *  @return based on the user decision to opt out or not, no exclusions or the ones coming from the extension points
   */
  private Set<IPath> getExclusions(String configScopeId) {
    Set<IPath> exclusions;
    if (SonarLintCorePlugin.loadConfig(this).isIndexingBasedOnEclipsePlugIns()) {
      exclusions = IProjectScopeProviderCache.INSTANCE.getEntry(configScopeId);
      if (exclusions == null) {
        exclusions = new HashSet<>();
        for (var projectScopeProvider : SonarLintExtensionTracker.getInstance().getProjectScopeProviders()) {
          exclusions.addAll(projectScopeProvider.getExclusions(project));
        }
        IProjectScopeProviderCache.INSTANCE.putEntry(configScopeId, exclusions);
      }
    } else {
      SonarLintLogger.get().traceIdeMessage("[DefaultSonarLintProjectAdapter#files] No exclusions calculated as '"
        + this.getName() + "' opted out of indexing based on other Eclipse plug-ins!");
      exclusions = new HashSet<>();
    }
    return exclusions;
  }

  public Collection<ISonarLintFile> getScmChangedFiles(IProgressMonitor monitor) {
    var result = new ArrayList<ISonarLintFile>();
    var provider = RepositoryProvider.getProvider(project);
    if (provider == null) {
      SonarLintLogger.get().debug("Project " + project.getName() + " doesn't have any RepositoryProvider");
      return result;
    }

    var subscriber = provider.getSubscriber();
    if (subscriber == null) {
      // Some providers (like Clear Case SCM Adapter) don't provide a Subscriber.
      SonarLintLogger.get().debug("No Subscriber for provider " + provider.getID() + " on project " + project.getName());
      return result;
    }

    try {
      var roots = subscriber.roots();
      if (List.of(roots).contains(project)) {
        // Refresh
        subscriber.refresh(new IResource[] {project}, IResource.DEPTH_INFINITE, monitor);

        // Collect all the synchronization states and print
        collect(subscriber, project, result);
      } else {
        SonarLintLogger.get().debug("Project " + project.getName() + " is not part of Subscriber roots");
      }
    } catch (TeamException e) {
      throw new IllegalStateException(UNABLE_TO_ANALYZE_CHANGED_FILES, e);
    }
    return result;
  }

  private static void collect(Subscriber subscriber, IResource resource, Collection<ISonarLintFile> changedFiles) throws TeamException {
    var file = SonarLintUtils.adapt(resource, IFile.class,
      "[DefaultSonarLintProjectAdapter#collect] Try get Eclipse file of resource '" + resource + "'");
    if (file != null) {
      var sonarLintFile = SonarLintUtils.adapt(file, ISonarLintFile.class,
        "[DefaultSonarLintProjectAdapter#collect] Try get file of Eclipse file '" + file + "'");
      if (sonarLintFile != null) {
        var syncInfo = subscriber.getSyncInfo(resource);
        if (syncInfo != null && !SyncInfo.isInSync(syncInfo.getKind())) {
          changedFiles.add(sonarLintFile);
        }
      }
    } else {
      for (var child : subscriber.members(resource)) {
        collect(subscriber, child, changedFiles);
      }
    }
  }

  @Override
  public IResource getResource() {
    return project;
  }

  @Override
  public int hashCode() {
    return project.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if ((obj == null) || (getClass() != obj.getClass())) {
      return false;
    }
    var other = (DefaultSonarLintProjectAdapter) obj;
    return Objects.equals(project, other.project);
  }

  // Because we are "inside" of our implementation of ISonarLintProject, we don't access ConfigScopeSynchronizer!
  private String getConfigScopeId() {
    return getResource().getLocationURI().toString();
  }
}
