/*
 * SonarQube Eclipse
 * Copyright (C) 2010-2015 SonarSource
 * sonarqube@googlegroups.com
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
package org.sonar.ide.eclipse.erlide.internal;

import java.util.Properties;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.erlide.engine.ErlangEngine;
import org.erlide.engine.model.IErlModel;
import org.erlide.engine.model.root.IErlProject;
import org.erlide.engine.util.NatureUtil;
import org.sonar.ide.eclipse.core.configurator.ProjectConfigurationRequest;
import org.sonar.ide.eclipse.core.configurator.ProjectConfigurator;
import org.sonar.ide.eclipse.core.configurator.SonarConfiguratorProperties;
import org.sonar.ide.eclipse.core.internal.SonarCorePlugin;

public class ErlangProjectConfigurator extends ProjectConfigurator {

  @Override
  public boolean canConfigure(IProject project) {
    return NatureUtil.hasErlangNature(project);
  }

  @Override
  public void configure(ProjectConfigurationRequest request, IProgressMonitor monitor) {
    IProject project = request.getProject();
    IErlModel model = ErlangEngine.getInstance().getModel();
    IErlProject erlProject = model.getErlangProject(project);
    configureErlangProject(project, erlProject, request.getSonarProjectProperties());
  }

  private void configureErlangProject(IProject project, IErlProject erlProject, Properties sonarProjectProperties) {
    for (IPath source : erlProject.getProperties().getSourceDirs()) {
      String relativeDir = getRelativePath(project.getLocation(), source);
      if (relativeDir == null) {
        SonarCorePlugin.getDefault().info("Skipping non existing source entry: " + source.toOSString());
        continue;
      }
      appendProperty(sonarProjectProperties, SonarConfiguratorProperties.TEST_DIRS_PROPERTY, relativeDir);
      appendProperty(sonarProjectProperties, SonarConfiguratorProperties.SOURCE_DIRS_PROPERTY, relativeDir);
    }
  }
}
