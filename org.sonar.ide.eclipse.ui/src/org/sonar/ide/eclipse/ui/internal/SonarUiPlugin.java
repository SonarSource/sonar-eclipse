/*
 * Sonar Eclipse
 * Copyright (C) 2010-2013 SonarSource
 * dev@sonar.codehaus.org
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.ide.eclipse.ui.internal;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.ide.eclipse.common.issues.IssueSeverity;
import org.sonar.ide.eclipse.core.internal.SonarCorePlugin;
import org.sonar.ide.eclipse.core.internal.jobs.SonarRunnerLogListener;
import org.sonar.ide.eclipse.core.internal.markers.MarkerUtils;
import org.sonar.ide.eclipse.core.internal.remote.IssuesUtils;
import org.sonar.ide.eclipse.core.internal.resources.SonarProject;
import org.sonar.ide.eclipse.core.internal.resources.SonarProperty;
import org.sonar.ide.eclipse.ui.internal.console.SonarConsole;
import org.sonar.ide.eclipse.ui.internal.jobs.SynchronizeIssuesJob;

import java.util.ArrayList;
import java.util.List;

public class SonarUiPlugin extends AbstractUIPlugin {

  // The shared instance
  private static SonarUiPlugin plugin;

  public static final String PREF_FILTER_ISSUES_MIN_SEVERITY = "filterIssuesMinSeverity"; //$NON-NLS-1$
  public static final String PREF_MARKER_SEVERITY = "markerSeverity"; //$NON-NLS-1$
  public static final String PREF_NEW_ISSUE_MARKER_SEVERITY = "newViolationMarkerSeverity"; //$NON-NLS-1$
  public static final String PREF_EXTRA_ARGS = "extraArgs"; //$NON-NLS-1$
  public static final String PREF_JVM_ARGS = "jvmArgs"; //$NON-NLS-1$

  private final Logger logger = LoggerFactory.getLogger(SonarUiPlugin.class);

  private IPropertyChangeListener listener;

  public SonarUiPlugin() {
    plugin = this;
  }

  @Override
  public void start(final BundleContext context) throws Exception {
    super.start(context);

    if (getSonarConsole() != null) {
      SonarCorePlugin.getDefault().addSonarRunnerLogListener((SonarRunnerLogListener) getSonarConsole());
    }

    SynchronizeIssuesJob.setupIssuesUpdater();

    listener = new IPropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent event) {
		if (event.getProperty().equals(PREF_FILTER_ISSUES_MIN_SEVERITY)) {
			IssuesUtils.setMinSeverityIssuesFilter(IssueSeverity
					.valueOf(getPreferenceStore().getString(
							PREF_FILTER_ISSUES_MIN_SEVERITY)));
		}
    	  
        if (event.getProperty().equals(PREF_MARKER_SEVERITY) || event.getProperty().equals(PREF_NEW_ISSUE_MARKER_SEVERITY)) {
          int newSeverity = getPreferenceStore().getInt(PREF_MARKER_SEVERITY);
          MarkerUtils.setMarkerSeverity(newSeverity);
          int newSeverityForNewIssues = getPreferenceStore().getInt(PREF_NEW_ISSUE_MARKER_SEVERITY);
          MarkerUtils.setMarkerSeverityForNewIssues(newSeverityForNewIssues);
          try {
            MarkerUtils.updateAllSonarMarkerSeverity();
          } catch (CoreException e) {
            logger.error("Unable to update marker severity", e);
          }
        }
      }
    };
    getPreferenceStore().addPropertyChangeListener(listener);
  }

  @Override
  public void stop(final BundleContext context) throws Exception {
    try {
      if (getSonarConsole() != null) {
        SonarCorePlugin.getDefault().removeSonarRunnerLogListener((SonarRunnerLogListener) getSonarConsole());
      }
      getPreferenceStore().removePropertyChangeListener(listener);
    } finally {
      super.stop(context);
    }
  }

  /**
   * @return the shared instance
   */
  public static SonarUiPlugin getDefault() {
    return plugin;
  }

  private SonarConsole console;

  public synchronized ISonarConsole getSonarConsole() {
    // Don't try to initialize console without actual UI - it will cause headless tests failure
    if ((console == null) && PlatformUI.isWorkbenchRunning()) {
      console = new SonarConsole(SonarImages.SONAR16_IMG);
    }
    return console;
  }

  /**
   * Initializes a preference store with default preference values
   * for this plug-in.
   */
  @Override
  protected void initializeDefaultPreferences(IPreferenceStore store) {
	store.setDefault(PREF_FILTER_ISSUES_MIN_SEVERITY, IssueSeverity.INFO.name());
    store.setDefault(PREF_MARKER_SEVERITY, IMarker.SEVERITY_WARNING);
    store.setDefault(PREF_NEW_ISSUE_MARKER_SEVERITY, IMarker.SEVERITY_ERROR);
    store.setDefault(PREF_EXTRA_ARGS, "");
    store.setDefault(PREF_JVM_ARGS, "");
    MarkerUtils.setMarkerSeverity(store.getInt(PREF_MARKER_SEVERITY));
    MarkerUtils.setMarkerSeverityForNewIssues(store.getInt(PREF_NEW_ISSUE_MARKER_SEVERITY));
    IssuesUtils.setMinSeverityIssuesFilter(IssueSeverity.valueOf(store.getString(PREF_FILTER_ISSUES_MIN_SEVERITY)));
  }

  public static List<SonarProperty> getExtraPropertiesForLocalAnalysis(IProject project) {
    List<SonarProperty> props = new ArrayList<SonarProperty>();
    // First add all global properties
    String globalExtraArgs = SonarUiPlugin.getDefault().getPreferenceStore().getString(SonarUiPlugin.PREF_EXTRA_ARGS);
    String[] keyValuePairs = StringUtils.split(globalExtraArgs, "\n\r");
    for (String keyValuePair : keyValuePairs) {
      String[] keyValue = keyValuePair.split("=");
      props.add(new SonarProperty(keyValue[0], keyValue[1]));
    }

    // Then add project properties
    SonarProject sonarProject = SonarProject.getInstance(project);
    if (sonarProject.getExtraProperties() != null) {
      props.addAll(sonarProject.getExtraProperties());
    }

    return props;
  }

  public static String getSonarJvmArgs() {
    return SonarUiPlugin.getDefault().getPreferenceStore().getString(SonarUiPlugin.PREF_JVM_ARGS);
  }

}
