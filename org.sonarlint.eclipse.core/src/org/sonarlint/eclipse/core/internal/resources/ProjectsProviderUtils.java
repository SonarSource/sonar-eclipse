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
package org.sonarlint.eclipse.core.internal.resources;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.backend.ConfigScopeSynchronizer;
import org.sonarlint.eclipse.core.internal.extension.SonarLintExtensionTracker;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.core.resource.ISonarLintProjectsProvider;

public class ProjectsProviderUtils {

  private ProjectsProviderUtils() {
    // Utility class
  }

  public static Collection<ISonarLintProject> allProjects() {
    return SonarLintExtensionTracker.getInstance().getProjectsProviders().stream()
      .map(ISonarLintProjectsProvider::get)
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());
  }

  /**
   *  Useful when we want SonarLint to behave differently when
   *  - no project bound (ret = 0)
   *  - all projects bound (ret = 1)
   *  - at least one project bound (0 < ret < 1)
   */
  public static float boundToAllProjectsRatio() {
    var allProjects = allProjects();
    var numberOfAllProjects = allProjects.size();
    var boundProjects = allProjects.stream()
      .filter(prj -> SonarLintCorePlugin.getConnectionManager().resolveBinding(prj).isPresent())
      .collect(Collectors.toSet());
    var numberOfBoundProjects = boundProjects.size();
    return numberOfAllProjects == 0 ? 0 : (numberOfBoundProjects / numberOfAllProjects);
  }

  public static Set<String> allConfigurationScopeIds() {
    return allProjects().stream().map(ConfigScopeSynchronizer::getConfigScopeId)
      .collect(Collectors.toSet());
  }
}
