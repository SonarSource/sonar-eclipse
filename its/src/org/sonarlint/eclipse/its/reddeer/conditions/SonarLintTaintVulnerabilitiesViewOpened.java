/*
 * SonarLint for Eclipse ITs
 * Copyright (C) 2009-2024 SonarSource SA
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
package org.sonarlint.eclipse.its.reddeer.conditions;

import org.eclipse.reddeer.common.condition.WaitCondition;
import org.sonarlint.eclipse.its.reddeer.views.SonarLintTaintVulnerabilitiesView;

public class SonarLintTaintVulnerabilitiesViewOpened implements WaitCondition {
private final SonarLintTaintVulnerabilitiesView sonarLintTaintVulnerabilitiesView;
  
  public SonarLintTaintVulnerabilitiesViewOpened(SonarLintTaintVulnerabilitiesView sonarLintTaintVulnerabilitiesView) {
    this.sonarLintTaintVulnerabilitiesView = sonarLintTaintVulnerabilitiesView;
  }
  
  @Override
  public boolean test() {
    return sonarLintTaintVulnerabilitiesView.isOpen();
  }

  @Override
  public SonarLintTaintVulnerabilitiesView getResult() {
    return sonarLintTaintVulnerabilitiesView;
  }

  @Override
  public String description() {
    return "Taint Vulnerabilities view is opened";
  }

  @Override
  public String errorMessageWhile() {
    return "Taint Vulnerabilities view is still opened";
  }

  @Override
  public String errorMessageUntil() {
    return "Taint Vulnerabilities view is not yet opened";
  }
}
