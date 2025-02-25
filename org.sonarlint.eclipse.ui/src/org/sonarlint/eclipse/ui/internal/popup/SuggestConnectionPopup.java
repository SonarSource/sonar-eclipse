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
package org.sonarlint.eclipse.ui.internal.popup;

import java.util.List;
import java.util.Map;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintGlobalConfiguration;
import org.sonarlint.eclipse.ui.internal.SonarLintImages;
import org.sonarlint.eclipse.ui.internal.binding.ProjectSuggestionDto;
import org.sonarlint.eclipse.ui.internal.binding.assist.AssistSuggestConnectionJob;
import org.sonarlint.eclipse.ui.internal.dialog.SuggestConnectionDialog;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

/**
 *  Notification pop-up that is shown for each connection where there might be more than one project key which can have
 *  more than one local Eclipse project linked to it. If the user chooses to accept, all of the projects will be bound
 *  together and they don't have the option to select different ones.
 */
public class SuggestConnectionPopup extends AbstractSonarLintPopup {
  private final Either<String, String> serverUrlOrOrganization;
  private final Map<String, List<ProjectSuggestionDto>> projectMapping;
  @Nullable
  private final String sonarCloudRegion;

  public SuggestConnectionPopup(Either<String, String> serverUrlOrOrganization,
    Map<String, List<ProjectSuggestionDto>> projectMapping, @Nullable String sonarCloudRegion) {
    this.serverUrlOrOrganization = serverUrlOrOrganization;
    this.projectMapping = projectMapping;
    this.sonarCloudRegion = sonarCloudRegion;
  }

  @Override
  protected String getMessage() {
    String prefix;
    if (serverUrlOrOrganization.isLeft()) {
      prefix = "For the SonarQube Server '" + serverUrlOrOrganization.getLeft();
    } else {
      prefix = "For the SonarQube Cloud organization '" + serverUrlOrOrganization.getRight();
    }

    if (projectMapping.keySet().size() > 1) {
      return prefix + "' there are multiple projects that can be connected to local projects. Click 'More "
        + "Information' to see them all. Do you want to connect and bind the project?";
    }

    var projectKey = projectMapping.keySet().toArray()[0];
    var mappedProjects = projectMapping.get(projectKey);
    if (mappedProjects.size() > 1) {
      return prefix + "' the project '" + projectKey + "' can be connected to multiple local projects. Click 'More "
        + "Information' to see them all. Do you want to connect and bind the project?";
    }

    return prefix + "' the project '" + projectKey
      + "' can be connected to the local project '" + mappedProjects.get(0).getProject().getName()
      + "'. Do you want to connect and bind the project?";
  }

  protected void addDontAskAgainLink() {
    addLink("Don't ask again", e -> {
      SonarLintGlobalConfiguration.setNoConnectionSuggestions();
      close();
    });
  }

  protected void addMoreInformationLink() {
    addLink("More information", e -> {
      var dialog = new SuggestConnectionDialog(getParentShell(), serverUrlOrOrganization, projectMapping);
      dialog.open();
    });
  }

  @Override
  protected String getPopupShellTitle() {
    return "Connection Suggestion to "
      + (serverUrlOrOrganization.isLeft() ? "SonarQube Server" : "SonarQube Cloud");
  }

  @Override
  protected Image getPopupShellImage(int maximumHeight) {
    return serverUrlOrOrganization.isLeft()
      ? SonarLintImages.SONARQUBE_SERVER_ICON_IMG
      : SonarLintImages.SONARCLOUD_SERVER_ICON_IMG;
  }

  @Override
  protected void createContentArea(Composite composite) {
    super.createContentArea(composite);

    addLinkWithTooltip("Connect", "Connect to " + (serverUrlOrOrganization.isLeft() ? "server" : "organization"),
      e -> {
        var job = new AssistSuggestConnectionJob(serverUrlOrOrganization, projectMapping, sonarCloudRegion);
        job.schedule();

        close();
      });

    var projectKeys = projectMapping.keySet().toArray();
    if (projectKeys.length > 1 || projectMapping.get(projectKeys[0]).size() > 1) {
      addMoreInformationLink();
    }

    addDontAskAgainLink();
  }
}
