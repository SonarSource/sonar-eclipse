/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2024 SonarSource SA
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
package org.sonarlint.eclipse.m2e.internal;

import java.util.Collection;
import java.util.Collections;
import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.annotation.Nullable;
import org.sonarlint.eclipse.core.resource.ISonarLintFileAdapterParticipant;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.core.resource.ISonarLintProjectHierarchyProvider;

public class MavenModuleFilter implements ISonarLintFileAdapterParticipant, ISonarLintProjectHierarchyProvider {
  private final boolean isM2ePresent;
  private final boolean isMavenPresent;

  public MavenModuleFilter() {
    this.isM2ePresent = isM2ePresent();
    this.isMavenPresent = isMavenPresent();
  }

  private static boolean isM2ePresent() {
    try {
      Class.forName("org.eclipse.m2e.core.MavenPlugin");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private static boolean isMavenPresent() {
    try {
      Class.forName("org.apache.maven.project.MavenProject");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @Override
  public boolean exclude(IFile file) {
    if (isM2ePresent) {
      return M2eUtils.isInNestedModule(file);
    }
    return false;
  }

  @Override
  public boolean partOfHierarchy(ISonarLintProject project) {
    if (isM2ePresent && isMavenPresent) {
      return MavenUtils.isPartOfHierarchy(project);
    }
    return false;
  }

  @Override
  @Nullable
  public ISonarLintProject getRootProject(ISonarLintProject project) {
    if (isM2ePresent && isMavenPresent) {
      return MavenUtils.getRootProjectInWorkspace(project);
    }
    return null;
  }

  @Override
  public Collection<ISonarLintProject> getSubProjects(ISonarLintProject project) {
    if (isM2ePresent && isMavenPresent) {
      return MavenUtils.getProjectSubProjects(project);
    }
    return Collections.emptyList();
  }
}
