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
package org.sonarlint.eclipse.ui.internal.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintGlobalConfiguration;
import org.sonarlint.eclipse.ui.internal.SonarLintUiPlugin;
import org.sonarlint.eclipse.ui.internal.console.SonarLintConsole;

/** Default preferences on the workspace level */
public class SonarLintPreferencesInitializer extends AbstractPreferenceInitializer {

  @Override
  public void initializeDefaultPreferences() {
    var node = DefaultScope.INSTANCE.getNode(SonarLintUiPlugin.PLUGIN_ID);
    node.put(SonarLintConsole.P_SHOW_CONSOLE, SonarLintConsole.P_SHOW_CONSOLE_ON_ERROR);
    node.putBoolean(SonarLintConsole.P_VERBOSE_OUTPUT, false);
    node.putBoolean(SonarLintConsole.P_ANALYZER_OUTPUT, false);
    node.putBoolean(SonarLintConsole.P_IDE_TRACING_OUTPUT, false);
    node.putInt(SonarLintGlobalConfiguration.PREF_MARKER_SEVERITY, SonarLintGlobalConfiguration.PREF_MARKER_SEVERITY_DEFAULT);
    node.putBoolean(SonarLintGlobalConfiguration.PREF_ISSUE_INCLUDE_RESOLVED, false);
    node.putBoolean(SonarLintGlobalConfiguration.PREF_ISSUE_ONLY_NEW_CODE, false);
    node.put(SonarLintGlobalConfiguration.PREF_EXTRA_ARGS, SonarLintGlobalConfiguration.PREF_DEFAULT);
    node.put(SonarLintGlobalConfiguration.PREF_TEST_FILE_GLOB_PATTERNS, SonarLintGlobalConfiguration.PREF_TEST_FILE_GLOB_PATTERNS_DEFAULT);
    node.putBoolean(SonarLintGlobalConfiguration.PREF_SHOW_REGION_SELECTOR, false);
  }

}
