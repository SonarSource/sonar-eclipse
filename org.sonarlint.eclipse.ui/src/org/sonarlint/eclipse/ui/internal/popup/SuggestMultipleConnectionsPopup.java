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

import java.util.HashMap;
import java.util.List;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintGlobalConfiguration;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.SonarLintImages;
import org.sonarlint.eclipse.ui.internal.binding.ProjectSuggestionDto;
import org.sonarlint.eclipse.ui.internal.binding.assist.AssistSuggestConnectionJob;
import org.sonarlint.eclipse.ui.internal.dialog.SuggestMultipleConnectionSelectionDialog;
import org.sonarlint.eclipse.ui.internal.dialog.SuggestMultipleConnectionsDialog;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

/**
 *  Notification pop-up that is shown for each Eclipse project where more than one connection suggestion was found. In
 *  this case the user has to choose manually which one to use.
 */
public class SuggestMultipleConnectionsPopup extends AbstractSonarLintPopup {
  protected final ISonarLintProject project;
  protected final List<ConnectionSuggestionDto> suggestions;

  public SuggestMultipleConnectionsPopup(ISonarLintProject project, List<ConnectionSuggestionDto> suggestions) {
    this.project = project;
    this.suggestions = suggestions;
  }

  protected void addDontAskAgainLink() {
    addLink("Don't ask again", e -> {
      SonarLintGlobalConfiguration.setNoConnectionSuggestions();
      close();
    });
  }

  protected void addMoreInformationLink() {
    addLink("More information", e -> {
      var dialog = new SuggestMultipleConnectionsDialog(getParentShell(), project, suggestions);
      dialog.open();
    });
  }

  @Override
  protected String getPopupShellTitle() {
    return "SonarQube - Multiple Connection Suggestions found";
  }

  @Override
  protected Image getPopupShellImage(int maximumHeight) {
    return SonarLintImages.BALLOON_IMG;
  }

  @Override
  protected String getMessage() {
    return "The local project '" + project.getName() + "' can be connected based on different suggestions. Click "
      + "'More Information' to see them all. Do you want to choose which suggestion to use and then connect and bind "
      + "the project?";
  }

  @Override
  protected void createContentArea(Composite composite) {
    super.createContentArea(composite);

    addLinkWithTooltip("Choose suggestion", "Based on suggestion", e -> {
      close();

      // ask the user to select the suggestion they want to use
      var dialog = new SuggestMultipleConnectionSelectionDialog(getParentShell(), project, suggestions);
      dialog.open();
      var selection = (String) dialog.getFirstResult();
      if (selection == null) {
        return;
      }

      // from the dialog response we have to get back the actual connection suggestion
      var suggestion = dialog.getSuggestionFromElement(selection);

      var isFromSharedConnectedMode = suggestion.isFromSharedConfiguration();
      var isSonarQube = suggestion.getConnectionSuggestion().isLeft();
      var projectKey = isSonarQube
        ? suggestion.getConnectionSuggestion().getLeft().getProjectKey()
        : suggestion.getConnectionSuggestion().getRight().getProjectKey();
      var region = isSonarQube ? null : suggestion.getConnectionSuggestion().getRight().getRegion().name();

      Either<String, String> serverUrlOrOrganization;
      if (isSonarQube) {
        serverUrlOrOrganization = Either.forLeft(suggestion.getConnectionSuggestion().getLeft().getServerUrl());
      } else {
        serverUrlOrOrganization = Either.forRight(suggestion.getConnectionSuggestion().getRight().getOrganization());
      }

      var projectMapping = new HashMap<String, List<ProjectSuggestionDto>>();
      projectMapping.put(projectKey, List.of(new ProjectSuggestionDto(project, isFromSharedConnectedMode)));

      var job = new AssistSuggestConnectionJob(serverUrlOrOrganization, projectMapping, region);
      job.schedule();
    });

    addMoreInformationLink();
    addDontAskAgainLink();
  }
}
