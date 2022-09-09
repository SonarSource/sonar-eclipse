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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.resources.ProjectsProviderUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarsource.sonarlint.core.vcs.GitUtils;

abstract class AbstractEGitVcsFacade implements VcsFacade {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  @Override
  public String electBestMatchingBranch(ISonarLintProject project, Set<String> serverCandidateNames, String serverMainBranch) {
    return getRepo(project.getResource()).map(repo -> GitUtils.electBestMatchingServerBranchForCurrentHead(repo, serverCandidateNames, serverMainBranch)).orElse(serverMainBranch);
  }

  abstract Optional<Repository> getRepo(IResource resource);

  @Override
  @Nullable
  public String getCurrentCommitRef(ISonarLintProject project) {
    return getRepo(project.getResource()).map(this::getHeadRef).orElse(null);
  }

  @Nullable
  private String getHeadRef(Repository repo) {
    try {
      return Optional.ofNullable(repo.exactRef(Constants.HEAD)).map(Ref::toString).orElse(null);
    } catch (IOException e) {
      LOG.debug("Unable to get current commit", e);
      return null;
    }
  }

  @Override
  public void addHeadRefsChangeListener(Consumer<List<ISonarLintProject>> listener) {
    Repository.getGlobalListenerList().addRefsChangedListener(event -> {
      List<ISonarLintProject> affectedProjects = new ArrayList<>();
      ProjectsProviderUtils.allProjects().forEach(p -> getRepo(p.getResource()).ifPresent(repo -> {
        File repoDir = repo.getDirectory();
        if (repoDir != null && repoDir.equals(event.getRepository().getDirectory())) {
          affectedProjects.add(p);
        }
      }));
      if (!affectedProjects.isEmpty()) {
        listener.accept(affectedProjects);
      }
    });
  }

}
