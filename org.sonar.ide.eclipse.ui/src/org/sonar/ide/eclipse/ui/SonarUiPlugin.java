/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2010-2011 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.ide.eclipse.ui;

import java.net.Authenticator;
import java.net.ProxySelector;

import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.LoggerFactory;
import org.sonar.ide.eclipse.core.ISonarProject;
import org.sonar.ide.eclipse.core.SonarCorePlugin;
import org.sonar.ide.eclipse.internal.EclipseProxyAuthenticator;
import org.sonar.ide.eclipse.internal.EclipseProxySelector;
import org.sonar.ide.eclipse.internal.core.ISonarConstants;
import org.sonar.ide.eclipse.internal.project.SonarProjectManager;
import org.sonar.ide.eclipse.internal.ui.FavouriteMetricsManager;
import org.sonar.ide.eclipse.internal.ui.SonarImages;
import org.sonar.ide.eclipse.internal.ui.console.SonarConsole;
import org.sonar.ide.eclipse.internal.ui.jobs.RefreshViolationsJob;
import org.sonar.ide.eclipse.internal.ui.preferences.SonarUiPreferenceInitializer;
import org.sonar.ide.eclipse.internal.ui.properties.ProjectProperties;

public class SonarUiPlugin extends AbstractUIPlugin {

  // The shared instance
  private static SonarUiPlugin plugin;

  private static SonarProjectManager projectManager;

  private FavouriteMetricsManager favouriteMetricsManager = new FavouriteMetricsManager();

  public SonarUiPlugin() {
    plugin = this; // NOSONAR
  }

  public SonarProjectManager getProjectManager() {
    if (projectManager == null) {
      projectManager = new SonarProjectManager();
    }
    return projectManager;
  }

  public static FavouriteMetricsManager getFavouriteMetricsManager() {
    return getDefault().favouriteMetricsManager;
  }

  @Override
  public void start(final BundleContext context) throws Exception {
    super.start(context);

    setupProxy(context);
    RefreshViolationsJob.setupViolationsUpdater();

    getFavouriteMetricsManager().set(SonarUiPreferenceInitializer.getFavouriteMetrics());
  }

  @Override
  public void stop(final BundleContext context) throws Exception {
    try {
      SonarUiPreferenceInitializer.setFavouriteMetrics(getFavouriteMetricsManager().get());
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

  private void setupProxy(final BundleContext context) {
    ServiceReference proxyServiceReference = context.getServiceReference(IProxyService.class.getName());
    if (proxyServiceReference != null) {
      IProxyService proxyService = (IProxyService) context.getService(proxyServiceReference);
      ProxySelector.setDefault(new EclipseProxySelector(proxyService));
      Authenticator.setDefault(new EclipseProxyAuthenticator(proxyService));
    }
  }

  public void displayError(final int severity, final String msg, final Throwable t, final boolean shouldLog) {
    final IStatus status = new Status(severity, ISonarConstants.PLUGIN_ID, msg, t);
    final Display display = PlatformUI.getWorkbench().getDisplay();
    display.syncExec(new Runnable() {
      public void run() {
        ErrorDialog.openError(display.getActiveShell(), null, "Error", status);
      }
    });
  }

  public static boolean hasSonarNature(IProject project) {
    try {
      return project.hasNature(SonarCorePlugin.NATURE_ID);
    } catch (CoreException e) {
      LoggerFactory.getLogger(SonarUiPlugin.class).error(e.getMessage(), e);
      return false;
    }
  }

  public static boolean hasJavaNature(IProject project) {
    try {
      return project.hasNature("org.eclipse.jdt.core.javanature");
    } catch (CoreException e) {
      LoggerFactory.getLogger(SonarUiPlugin.class).error(e.getMessage(), e);
      return false;
    }
  }

  public static ISonarProject getSonarProject(IProject project) {
    return ProjectProperties.getInstance(project);
  }

  private SonarConsole console;

  public synchronized ISonarConsole getSonarConsole() {
    if (console == null) {
      console = new SonarConsole(SonarImages.SONAR16_IMG);
    }
    return console;
  }

}
