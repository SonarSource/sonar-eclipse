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

import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.core.resources.IMarker;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.documentation.SonarLintDocumentation;
import org.sonarlint.eclipse.core.internal.TriggerType;
import org.sonarlint.eclipse.core.internal.backend.SonarLintBackendService;
import org.sonarlint.eclipse.core.internal.jobs.TestFileClassifier;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintGlobalConfiguration;
import org.sonarlint.eclipse.core.internal.utils.JavaRuntimeUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.Messages;
import org.sonarlint.eclipse.ui.internal.SonarLintUiPlugin;
import org.sonarlint.eclipse.ui.internal.binding.actions.AnalysisJobsScheduler;
import org.sonarlint.eclipse.ui.internal.job.OpenIssueInEclipseJob;
import org.sonarlint.eclipse.ui.internal.job.OpenIssueInEclipseJob.OpenIssueContext;
import org.sonarlint.eclipse.ui.internal.job.TaintIssuesJobsScheduler;
import org.sonarlint.eclipse.ui.internal.util.BrowserUtils;
import org.sonarlint.eclipse.ui.internal.util.PlatformUtils;

/**
 * Preference page for the workspace.
 */
public class SonarLintPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
  public static final String ID = "org.sonarlint.eclipse.ui.preferences.SonarLintPreferencePage";

  // when we ask the user to change the preferences on "Open in IDE" feature;
  @Nullable
  private OpenIssueContext openIssueContext;

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
    var labelLayoutData = new GridData(SWT.LEFT, SWT.DOWN, true, false, Integer.MAX_VALUE, 1);

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

    PlatformUtils.createHorizontalSpacer(getFieldEditorParent(), 1);

    addField(new BooleanFieldEditor(SonarLintGlobalConfiguration.PREF_ISSUE_INCLUDE_RESOLVED,
      "Show SonarQube markers for resolved issues as well",
      getFieldEditorParent()));

    // INFO: For the label to take up all the horizontal space in the grid (the size we cannot get), we have to use a
    // high span as it will be set internally to the actual grid width if ours is too big: Otherwise the
    // settings label from the line below would shift one row up!
    var issueFilterLabel = new Link(getFieldEditorParent(), SWT.NONE);
    issueFilterLabel.setText("SonarQube markers can be resolved from within your IDE. <a>Learn how</a>.");
    issueFilterLabel.setLayoutData(labelLayoutData);
    issueFilterLabel.addListener(SWT.Selection,
      e -> BrowserUtils.openExternalBrowser(SonarLintDocumentation.MARK_ISSUES_LINK, e.display));

    PlatformUtils.createHorizontalSpacer(getFieldEditorParent(), 1);

    addField(new BooleanFieldEditor(SonarLintGlobalConfiguration.PREF_ISSUE_ONLY_NEW_CODE,
      "Show SonarQube markers only for new code",
      getFieldEditorParent()));

    var issuePeriodLabel = new Link(getFieldEditorParent(), SWT.NONE);
    issuePeriodLabel.setText("Focusing on new code helps you practice <a>Clean as You Code</a>.");
    issuePeriodLabel.setToolTipText("In Standalone Mode, any code added or changed in the last 30 days is considered "
      + "new code. Projects in Connected Mode can benefit from a more accurate new code definition based on your "
      + "SonarQube (Server, Cloud) settings.");
    issuePeriodLabel.setLayoutData(labelLayoutData);
    issuePeriodLabel.addListener(SWT.Selection,
      e -> BrowserUtils.openExternalBrowser(SonarLintDocumentation.CLEAN_AS_YOU_CODE, e.display));
    
    PlatformUtils.createHorizontalSpacer(getFieldEditorParent(), 1);

    addField(new BooleanFieldEditor(SonarLintGlobalConfiguration.PREF_SHOW_REGION_SELECTOR,
      "(Early Access) Show region selection for SonarQube Cloud",
      getFieldEditorParent()));

    PlatformUtils.createHorizontalSpacer(getFieldEditorParent(), 1);

    var powerUserLabel = new Link(getFieldEditorParent(), SWT.NONE);
    powerUserLabel.setText("This section targets power users who want to tweak SonarQube for Eclipse even more. "
      + "Please refer to <a>the documentation</a>.");
    powerUserLabel.setLayoutData(labelLayoutData);
    powerUserLabel.addListener(SWT.Selection,
      e -> BrowserUtils.openExternalBrowser(SonarLintDocumentation.PROVIDE_JAVA_RUNTIME_LINK, e.display));

    addField(new Java17Field(getFieldEditorParent()));
  }

  private static class NodeJsField extends AbstractPathField {
    private static final String NODE_JS_TOOLTIP = "SonarQube requires Node.js to analyze some languages. You can "
      + "provide an explicit path for the node executable here or leave this field blank to let SonarLint look for "
      + "it using your PATH environment variable.";

    public NodeJsField(Composite parent) {
      super(SonarLintGlobalConfiguration.PREF_NODEJS_PATH, "Node.js executable path:", parent, false);
    }

    @Override
    void provideDefaultValue() {
      getTextControl().setToolTipText(NODE_JS_TOOLTIP);

      String detectedNodeJs;
      try {
        var nodeJs = SonarLintBackendService.get().getBackend().getAnalysisService().getGlobalStandaloneConfiguration()
          .join()
          .getNodeJsDetails();
        if (nodeJs != null) {
          detectedNodeJs = nodeJs.getPath().toString();
        } else {
          detectedNodeJs = "Node.js not found";
        }
      } catch (Exception err) {
        // JSON-RPC error or backend not initialized -> shouldn't impact the preference page
        SonarLintLogger.get().debug("SonarLint backend not responding on Node.js", err);
        detectedNodeJs = "Node.js not found, backend not responding";
      }
      getTextControl().setMessage(detectedNodeJs);
    }

    /** INFO: For now we don't check if the Node.js version is actually correct and supported, we can do so in the future! */
    @Override
    boolean checkStateFurther(Path value) {
      return true;
    }
  }

  private static class Java17Field extends AbstractPathField {
    private static final String JAVA_17_TOOLTIP = "SonarQube for Eclipse provides its own JRE to run part of the "
      + "plug-in out of process if Eclipse is not running with a Java 17+ one that can be used. You can provide an "
      + "explicit Java 17+ installation to be used instead, e.g. when your IDE is running on Java 16 or lower. But be "
      + "cautious as it is your responsibility to make sure that it works correctly!";

    public Java17Field(Composite parent) {
      super(SonarLintGlobalConfiguration.PREF_JAVA17_PATH, "Java 17+ installation path:", parent, true);
    }

    @Override
    void provideDefaultValue() {
      getTextControl().setToolTipText(JAVA_17_TOOLTIP);

      var javaRuntimeInformation = JavaRuntimeUtils.getJavaRuntime();
      switch (javaRuntimeInformation.getProvider()) {
        case SELF_MANAGED:
          getTextControl().setMessage(javaRuntimeInformation.getPath().toString());
          break;
        case ECLIPSE_MANAGED:
          getTextControl().setMessage("Using Java installation of Eclipse");
          break;
        case SONARLINT_BUNDLED:
          getTextControl().setMessage("Using Java installation of SonarQube for Eclipse");
      }
    }

    /** INFO: For now we only check for the Java executable being present, not if actually Java 17+, we can do so in the future! */
    @Override
    boolean checkStateFurther(Path value) {
      var exists = JavaRuntimeUtils.checkForJavaExecutable(value);
      if (!exists) {
        setErrorMessage("Java executable could not be found inside: " + value.resolve("bin").toString());
      }
      return exists;
    }
  }

  @Override
  public boolean performOk() {
    var issuesIncludingResolved = SonarLintGlobalConfiguration.issuesIncludingResolved();
    var issuesOnlyNewCode = SonarLintGlobalConfiguration.issuesOnlyNewCode();
    var previousTestFileGlobPatterns = SonarLintGlobalConfiguration.getTestFileGlobPatterns();
    var previousNodeJsPath = SonarLintGlobalConfiguration.getNodejsPath();
    var previousJava17Path = SonarLintGlobalConfiguration.getJava17Path();
    var result = super.performOk();
    var anyPreferenceChanged = false;

    var issueFilterChanged = issuesIncludingResolved != SonarLintGlobalConfiguration.issuesIncludingResolved();
    var issuePeriodChanged = issuesOnlyNewCode != SonarLintGlobalConfiguration.issuesOnlyNewCode();
    if (issueFilterChanged || issuePeriodChanged) {
      TaintIssuesJobsScheduler.scheduleUpdateAfterPreferenceChange();
      anyPreferenceChanged = true;
    }
    if (!previousTestFileGlobPatterns.equals(SonarLintGlobalConfiguration.getTestFileGlobPatterns())) {
      TestFileClassifier.get().reload();
      anyPreferenceChanged = true;
    }
    if (!Objects.equals(previousNodeJsPath, SonarLintGlobalConfiguration.getNodejsPath())) {
      anyPreferenceChanged = true;
    }
    if (!Objects.equals(previousJava17Path, SonarLintGlobalConfiguration.getJava17Path())) {
      // Will be implemented with SLE-812 to restart Sloop!
    }
    if (anyPreferenceChanged) {
      AnalysisJobsScheduler.scheduleAnalysisOfOpenFiles((ISonarLintProject) null, TriggerType.STANDALONE_CONFIG_CHANGE);
    }
    if (openIssueContext != null) {
      // INFO: We cannot schedule it immediately as the OpenIssueInEclipseJob might be faster than the preferences
      // dialog closing. It will focus the MessageDialog when the issue cannot be found but in order to access it
      // we have to close the preferences dialog which cannot be focused.
      // -> This is a corner case but just in case (e.g. ITs crashing on our side because they're too fast).
      new OpenIssueInEclipseJob(openIssueContext).schedule(1000);
    }

    return result;
  }

  public void setOpenIssueInEclipseJobParams(OpenIssueContext context) {
    this.openIssueContext = context;
  }
}
