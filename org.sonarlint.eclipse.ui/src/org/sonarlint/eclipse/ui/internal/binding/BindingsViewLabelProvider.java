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
package org.sonarlint.eclipse.ui.internal.binding;

import java.util.stream.Collectors;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE.SharedImages;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.engine.connected.ConnectionFacade;
import org.sonarlint.eclipse.core.internal.engine.connected.SonarProject;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintGlobalConfiguration;
import org.sonarlint.eclipse.core.internal.utils.StringUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.SonarLintImages;
import org.sonarlint.eclipse.ui.internal.SonarLintUiPlugin;

public class BindingsViewLabelProvider extends BaseCellLabelProvider {

  @Override
  public String getText(Object element) {
    if (element instanceof ConnectionFacade) {
      var connection = (ConnectionFacade) element;
      var regionPrefix = getRegionPrefix(connection);
      return regionPrefix.concat(StringUtils.defaultString(connection.getId()));
    }
    if (element instanceof SonarProject) {
      return ((SonarProject) element).getName();
    }
    if (element instanceof ISonarLintProject) {
      return ((ISonarLintProject) element).getName();
    }
    if (element instanceof IWorkspaceRoot) {
      return Platform.getResourceString(SonarLintUiPlugin.getDefault().getBundle(), "%viewServers");
    }

    return "";
  }

  @Override
  public Image getImage(Object element) {
    if (element instanceof ConnectionFacade) {
      if (((ConnectionFacade) element).isSonarCloud()) {
        return SonarLintImages.SONARCLOUD_SERVER_ICON_IMG;
      } else {
        return SonarLintImages.SONARQUBE_SERVER_ICON_IMG;
      }
    }
    if (element instanceof SonarProject) {
      return SonarLintImages.SONARQUBE_PROJECT_ICON_IMG;
    }
    if (element instanceof ISonarLintProject) {
      return PlatformUI.getWorkbench().getSharedImages().getImage(SharedImages.IMG_OBJ_PROJECT);
    }
    return null;
  }

  @Override
  public Image getColumnImage(Object element, int index) {
    // Left blank since the CNF doesn't support this
    return null;
  }

  @Override
  public String getColumnText(Object element, int index) {
    // Left blank since the CNF doesn't support this
    return null;
  }
  
  private static String getRegionPrefix(ConnectionFacade connection) {
    var allConnections = SonarLintCorePlugin.getConnectionManager().getConnections();
    var sonarQubeCloudConnectionCount = allConnections.stream().filter(c -> c.isSonarCloud()).collect(Collectors.toList()).size();
    var regionLabel = StringUtils.isNotBlank(connection.getSonarCloudRegion()) ? connection.getSonarCloudRegion() : "EU";
    return SonarLintGlobalConfiguration.shouldShowRegionSelection() &&
      connection.isSonarCloud() &&
      sonarQubeCloudConnectionCount > 1 ? String.format("[%s] ", regionLabel) : "";
  }

}
