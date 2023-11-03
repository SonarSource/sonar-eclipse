/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2023 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal.documentation;

public class SonarLintDocumentation {
  private static final String BASE_DOCS_URL = "https://docs.sonarsource.com/sonarlint/eclipse";
  public static final String CONNECTED_MODE_LINK = BASE_DOCS_URL + "/team-features/connected-mode/";
  public static final String VERSION_SUPPORT_POLICY = BASE_DOCS_URL + "/team-features/connected-mode/#sonarlint-sonarqube-version-support-policy";
  public static final String SECURITY_HOTSPOTS_LINK = BASE_DOCS_URL + "/using-sonarlint/security-hotspots/";
  public static final String TAINT_VULNERABILITIES_LINK = BASE_DOCS_URL + "/using-sonarlint/taint-vulnerabilities/";
  public static final String ON_THE_FLY_VIEW_LINK = BASE_DOCS_URL + "/using-sonarlint/investigating-issues/#the-on-the-fly-view";
  public static final String REPORT_VIEW_LINK = BASE_DOCS_URL + "/using-sonarlint/investigating-issues/#the-report-view";
  public static final String ISSUE_PERIOD_LINK = BASE_DOCS_URL + "/using-sonarlint/investigating-issues/#focusing-on-new-code";
  public static final String MARK_ISSUES_LINK = BASE_DOCS_URL + "/using-sonarlint/fixing-issues/#marking-issues";

  private SonarLintDocumentation() {
    // utility class
  }
}
