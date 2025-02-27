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
package org.sonarlint.eclipse.ui.internal.binding;

import org.sonarlint.eclipse.core.resource.ISonarLintProject;

/** Pairing the information of a project and whether its suggestion is coming from shared configuration or not */
public class ProjectSuggestionDto {
  private final ISonarLintProject project;
  private final boolean isFromSharedConfiguration;

  public ProjectSuggestionDto(ISonarLintProject project, boolean isFromSharedConfiguration) {
    this.project = project;
    this.isFromSharedConfiguration = isFromSharedConfiguration;
  }

  public ISonarLintProject getProject() {
    return this.project;
  }

  public boolean getIsFromSharedConfiguration() {
    return this.isFromSharedConfiguration;
  }
}
