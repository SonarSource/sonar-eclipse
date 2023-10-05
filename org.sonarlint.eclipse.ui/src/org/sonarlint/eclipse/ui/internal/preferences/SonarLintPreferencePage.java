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
package org.sonarlint.eclipse.ui.internal.preferences;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.core.resources.IMarker;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringButtonFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.TriggerType;
import org.sonarlint.eclipse.core.internal.backend.SonarLintBackendService;
import org.sonarlint.eclipse.core.internal.jobs.TestFileClassifier;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintGlobalConfiguration;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.Messages;
import org.sonarlint.eclipse.ui.internal.SonarLintUiPlugin;
import org.sonarlint.eclipse.ui.internal.binding.actions.AnalysisJobsScheduler;
import org.sonarlint.eclipse.ui.internal.documentation.SonarLintDocumentation;
import org.sonarlint.eclipse.ui.internal.job.TaintIssuesJobsScheduler;
import org.sonarlint.eclipse.ui.internal.util.BrowserUtils;

/**
 * Preference page for the workspace.
 */
public class SonarLintPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

  public static final String ID = "org.sonarlint.eclipse.ui.preferences.SonarLintPreferencePage";
  private static final String NODE_JS_TOOLTIP = "SonarLint requires Node.js to analyze some languages. "
    + "You can provide an explicit path for the node executable here or leave this field blank to let SonarLint look for it using your PATH environment variable.";

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
    addField(new ComboFieldEditor(SonarLintGlobalConfiguration.PREF_MARKER_SEVERITY,
      Messages.SonarPreferencePage_label_marker_severity,
      new String[][] {
        {"Info", String.valueOf(IMarker.SEVERITY_INFO)},
        {"Warning", String.valueOf(IMarker.SEVERITY_WARNING)},
        {"Error", String.valueOf(IMarker.SEVERITY_ERROR)}},
      getFieldEditorParent()));
    addField(new StringFieldEditor(SonarLintGlobalConfiguration.PREF_TEST_FILE_GLOB_PATTERNS,
      Messages.SonarPreferencePage_label_test_file_glob_patterns, getFieldEditorParent()));
    addField(new NodeJsField(getFieldEditorParent()));
    
    // INFO: For the label to take up all the horizontal space in the grid (the size we cannot get), we have to use a
    //       high span as it will be set internally to the actual grid width if ours is too big: Otherwise the the
    //       settings label from the line below would shift one row up!
    var issuePeriodLabel = new Link(getFieldEditorParent(), SWT.NONE);
    issuePeriodLabel.setText("This preference only applies for projects in connected mode with <a>SonarQube / SonarCloud</a>:");
    issuePeriodLabel.setLayoutData(new GridData(SWT.LEFT, SWT.DOWN, true, false, Integer.MAX_VALUE, 1));
    issuePeriodLabel.addListener(SWT.Selection,
      e -> BrowserUtils.openExternalBrowser(SonarLintDocumentation.ISSUE_PERIOD_LINK, e.display));
    
    addField(new ComboFieldEditor(SonarLintGlobalConfiguration.PREF_ISSUE_PERIOD,
      Messages.SonarPreferencePage_label_issue_period,
      new String[][] {
        {"Since the beginning", SonarLintGlobalConfiguration.PREF_ISSUE_PERIOD_ALLTIME},
        {"Only on new code", SonarLintGlobalConfiguration.PREF_ISSUE_PERIOD_NEWCODE}},
      getFieldEditorParent()));
  }

  private static class NodeJsField extends StringButtonFieldEditor {

    public NodeJsField(Composite parent) {
      super(SonarLintGlobalConfiguration.PREF_NODEJS_PATH, "Node.js executable path:", parent);
      setChangeButtonText("Browse...");
    }

    @Override
    protected void doFillIntoGrid(Composite parent, int numColumns) {
      super.doFillIntoGrid(parent, numColumns);
      getTextControl().setToolTipText(NODE_JS_TOOLTIP);
      final var detectedNodeJsPath = SonarLintCorePlugin.getNodeJsManager().getNodeJsPath();
      getTextControl().setMessage(detectedNodeJsPath != null ? detectedNodeJsPath.toString() : "Node.js not found");
    }

    @Override
    protected boolean doCheckState() {
      var stringValue = getStringValue();
      Path path;
      try {
        path = Paths.get(stringValue);
      } catch (InvalidPathException e) {
        setErrorMessage("Invalid path: " + stringValue);
        return false;
      }
      if (!Files.exists(path)) {
        setErrorMessage("File doesn't exist: " + stringValue);
        return false;
      }
      return true;
    }

    @Nullable
    @Override
    protected String changePressed() {
      var dialog = new FileDialog(getShell(), SWT.OPEN);
      if (getStringValue().trim().length() > 0) {
        dialog.setFileName(getStringValue());
      }
      var file = dialog.open();
      if (file != null) {
        file = file.trim();
        if (file.length() > 0) {
          return file;
        }
      }
      return null;
    }

  }

  @Override
  public boolean performOk() {
    var previousIssuePeriod = SonarLintGlobalConfiguration.getIssuePeriod();
    var previousTestFileGlobPatterns = SonarLintGlobalConfiguration.getTestFileGlobPatterns();
    var previousNodeJsPath = SonarLintGlobalConfiguration.getNodejsPath();
    var result = super.performOk();
    var anyPreferenceChanged = false;
    if (!previousIssuePeriod.equals(SonarLintGlobalConfiguration.getIssuePeriod())) {
      SonarLintBackendService.get().notifyTelemetryAfterNewCodePreferenceChanged();
      TaintIssuesJobsScheduler.scheduleUpdateAfterNewCodePeriodChange();
      anyPreferenceChanged = true;
    }
    if (!previousTestFileGlobPatterns.equals(SonarLintGlobalConfiguration.getTestFileGlobPatterns())) {
      TestFileClassifier.get().reload();
      anyPreferenceChanged = true;
    }
    if (!previousNodeJsPath.equals(SonarLintGlobalConfiguration.getNodejsPath())) {
      SonarLintCorePlugin.getNodeJsManager().reload();
      anyPreferenceChanged = true;
    }
    if (anyPreferenceChanged) {
      AnalysisJobsScheduler.scheduleAnalysisOfOpenFiles((ISonarLintProject) null, TriggerType.STANDALONE_CONFIG_CHANGE);
    }

    return result;
  }

}
