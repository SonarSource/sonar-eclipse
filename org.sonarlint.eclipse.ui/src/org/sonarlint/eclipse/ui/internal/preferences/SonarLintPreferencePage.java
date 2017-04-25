/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2017 SonarSource SA
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

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.sonarlint.eclipse.core.internal.PreferencesUtils;
import org.sonarlint.eclipse.ui.internal.Messages;
import org.sonarlint.eclipse.ui.internal.SonarLintUiPlugin;

/**
 * Preference page for the workspace.
 */
public class SonarLintPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

  /**
   * Value is persisted by the telemetry framework. We use an Eclipse property "just" to benefit from automatic UI.
   */
  public static final String PREF_TELEMETRY = "telemetry";

  public SonarLintPreferencePage() {
    super(Messages.SonarPreferencePage_title, GRID);
  }

  @Override
  public void init(IWorkbench workbench) {
    setDescription(Messages.SonarPreferencePage_description);
    setPreferenceStore(SonarLintUiPlugin.getDefault().getPreferenceStore());
  }

  @Override
  protected void createFieldEditors() {

    addField(new ComboFieldEditor(PreferencesUtils.PREF_MARKER_SEVERITY,
      Messages.SonarPreferencePage_label_marker_severity,
      new String[][] {
        {"Info", String.valueOf(IMarker.SEVERITY_INFO)},
        {"Warning", String.valueOf(IMarker.SEVERITY_WARNING)},
        {"Error", String.valueOf(IMarker.SEVERITY_ERROR)}},
      getFieldEditorParent()));
    addField(new StringFieldEditor(PreferencesUtils.PREF_TEST_FILE_REGEXPS,
      Messages.SonarPreferencePage_label_test_file_regexps, getFieldEditorParent()));
    addField(new LabelFieldEditor("Telemetry", "By sharing anonymous SonarLint usage statistics, you help us understand\nhow SonarLint is used so "
      + "we can improve the plugin to work even better for you.\nWe don't collect source code, IP addresses, or any personally identifying "
      + "information.\n And we don't share the data with anyone else.", getFieldEditorParent()));
    addField(new BooleanFieldEditor(PREF_TELEMETRY, "Share anonymous SonarLint statistics", BooleanFieldEditor.SEPARATE_LABEL, getFieldEditorParent()));
  }

}
