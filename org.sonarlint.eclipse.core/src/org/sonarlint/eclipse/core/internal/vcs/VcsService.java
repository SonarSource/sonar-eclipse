/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2022 SonarSource SA
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
package org.sonarlint.eclipse.core.internal.vcs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jdt.annotation.Nullable;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.engine.connected.ResolvedBinding;
import org.sonarlint.eclipse.core.internal.jobs.StorageSynchronizerJob;
import org.sonarlint.eclipse.core.internal.utils.BundleUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;

import static java.util.stream.Collectors.joining;

public class VcsService {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static final boolean IS_EGIT_5_12_BUNDLE_AVAILABLE = BundleUtils.isBundleInstalledWithMinVersion("org.eclipse.egit.core", 5, 12);
  public static final boolean IS_EGIT_UI_BUNDLE_AVAILABLE = BundleUtils.isBundleInstalled("org.eclipse.egit.ui");

  private static final Map<ISonarLintProject, Object> previousCommitRefCache = new ConcurrentHashMap<>();
  private static final Map<ISonarLintProject, String> electedServerBranchCache = new ConcurrentHashMap<>();

  private VcsService() {
  }

  public static VcsFacade getFacade() {
    // For now we only support eGit
    if (IS_EGIT_5_12_BUNDLE_AVAILABLE) {
      return new EGit5dot12VcsFacade();
    }
    if (IS_EGIT_UI_BUNDLE_AVAILABLE) {
      return new OldEGitVcsFacade();
    }
    return new NoOpVcsFacade();
  }

  private static String electBestMatchingBranch(VcsFacade facade, ISonarLintProject project) {
    LOG.debug("Elect best matching branch for project " + project.getName() + "...");
    Optional<ResolvedBinding> bindingOpt = SonarLintCorePlugin.getServersManager().resolveBinding(project);
    if (bindingOpt.isEmpty()) {
      electedServerBranchCache.remove(project);
      previousCommitRefCache.remove(project);
      throw new IllegalStateException("Project " + project.getName() + " is not bound");
    }

    var serverBranches = bindingOpt.get().getEngineFacade().getServerBranches(bindingOpt.get().getProjectBinding().projectKey());
    LOG.debug("Find best matching branch among: " + serverBranches.getBranchNames().stream().collect(joining(",")));
    var matched = facade.electBestMatchingBranch(project, serverBranches.getBranchNames(), serverBranches.getMainBranchName());
    LOG.debug("Best matching branch is " + matched);
    return matched;
  }

  private static void saveCurrentCommitRef(ISonarLintProject project, VcsFacade facade) {
    Object newCommitRef = facade.getCurrentCommitRef(project);
    if (newCommitRef == null) {
      previousCommitRefCache.remove(project);
    } else {
      previousCommitRefCache.put(project, newCommitRef);
    }
  }

  private static boolean shouldRecomputeMatchingBranch(ISonarLintProject project, @Nullable Object newCommitRef) {
    Object previousCommitRef = previousCommitRefCache.get(project);
    if (!Objects.equals(previousCommitRef, newCommitRef)) {
      LOG.debug("HEAD has changed since last election, evict cached branch...");
      return true;
    }
    return false;
  }

  public static void projectClosed(ISonarLintProject project) {
    previousCommitRefCache.remove(project);
    electedServerBranchCache.remove(project);
  }

  public static void clearVcsCache() {
    previousCommitRefCache.clear();
    electedServerBranchCache.clear();
  }

  public static String getServerBranch(ISonarLintProject project) {
    return electedServerBranchCache.computeIfAbsent(project, p -> {
      VcsFacade facade = getFacade();
      saveCurrentCommitRef(project, facade);
      return electBestMatchingBranch(facade, p);
    });
  }

  public static void installBranchChangeListener() {
    getFacade().addHeadRefsChangeListener(projects -> {
      List<ISonarLintProject> projectsToSync = new ArrayList<>();
      projects.forEach(project -> {
        Optional<ResolvedBinding> bindingOpt = SonarLintCorePlugin.getServersManager().resolveBinding(project);
        if (bindingOpt.isEmpty()) {
          return;
        }
        VcsFacade facade = getFacade();
        Object newCommitRef = facade.getCurrentCommitRef(project);
        if (shouldRecomputeMatchingBranch(project, newCommitRef)) {
          saveCurrentCommitRef(project, facade);
          var previousElectedBranch = electedServerBranchCache.get(project);
          var newElectedBranch = electBestMatchingBranch(facade, project);
          if (!newElectedBranch.equals(previousElectedBranch)) {
            electedServerBranchCache.put(project, newElectedBranch);
            projectsToSync.add(project);
          }
        }
      });
      if (!projectsToSync.isEmpty()) {
        new StorageSynchronizerJob(projectsToSync).schedule();
      }
    });
  }

}
