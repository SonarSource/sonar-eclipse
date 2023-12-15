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
package org.sonarlint.eclipse.core.documentation;

public class SonarLintDocumentation {

  private static final String BASE_DOCS_URL = "https://docs.sonarsource.com/sonarlint/eclipse";
  public static final String CONNECTED_MODE_LINK = BASE_DOCS_URL + "/team-features/connected-mode/";
  public static final String CONNECTED_MODE_BENEFITS = CONNECTED_MODE_LINK + "#benefits";
  public static final String VERSION_SUPPORT_POLICY = CONNECTED_MODE_LINK + "#sonarlint-sonarqube-version-support-policy";
  public static final String BRANCH_AWARENESS = CONNECTED_MODE_LINK + "#branch-awareness";
  public static final String SECURITY_HOTSPOTS_LINK = BASE_DOCS_URL + "/using-sonarlint/security-hotspots/";
  public static final String TAINT_VULNERABILITIES_LINK = BASE_DOCS_URL + "/using-sonarlint/taint-vulnerabilities/";
  public static final String ON_THE_FLY_VIEW_LINK = BASE_DOCS_URL + "/using-sonarlint/investigating-issues/#the-on-the-fly-view";
  public static final String REPORT_VIEW_LINK = BASE_DOCS_URL + "/using-sonarlint/investigating-issues/#the-report-view";
  public static final String ISSUE_PERIOD_LINK = BASE_DOCS_URL + "/using-sonarlint/investigating-issues/#focusing-on-new-code";
  public static final String MARK_ISSUES_LINK = BASE_DOCS_URL + "/using-sonarlint/fixing-issues/#marking-issues";
  public static final String FILE_EXCLUSIONS = BASE_DOCS_URL + "/using-sonarlint/file-exclusions/";

  private static final String BASE_MARKETING_URL = "https://www.sonarsource.com";
  public static final String COMPARE_SERVER_PRODUCTS_LINK = BASE_MARKETING_URL + "/open-source-editions";
  public static final String SONARQUBE_EDITIONS_LINK = BASE_MARKETING_URL + "/open-source-editions/sonarqube-community-edition";
  public static final String SONARCLOUD_PRODUCT_LINK = BASE_MARKETING_URL + "/products/sonarcloud";

  private SonarLintDocumentation() {
    // utility class
  }

}
