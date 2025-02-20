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
package org.sonarlint.eclipse.ui.internal.binding.assist;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.ui.PlatformUI;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.backend.ConfigScopeSynchronizer;
import org.sonarlint.eclipse.core.internal.backend.SonarLintBackendService;
import org.sonarlint.eclipse.core.internal.engine.connected.ConnectionFacade;
import org.sonarlint.eclipse.core.internal.jobs.EnableBindingSuggestionsJob;
import org.sonarlint.eclipse.core.internal.telemetry.SonarLintTelemetry;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.binding.ProjectSuggestionDto;
import org.sonarlint.eclipse.ui.internal.binding.wizard.connection.AbstractConnectionWizard;
import org.sonarlint.eclipse.ui.internal.binding.wizard.connection.ServerConnectionModel;
import org.sonarlint.eclipse.ui.internal.binding.wizard.connection.SuggestConnectionWizard;
import org.sonarlint.eclipse.ui.internal.binding.wizard.project.ProjectBindingProcess;
import org.sonarlint.eclipse.ui.internal.popup.ProjectBoundPopup;
import org.sonarlint.eclipse.ui.internal.popup.ProjectKeyNotFoundPopup;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;

public class AssistSuggestConnectionJob extends AbstractAssistCreatingConnectionJob {
  private final Map<String, List<ProjectSuggestionDto>> projectMapping;

  public AssistSuggestConnectionJob(Either<String, String> serverUrlOrOrganization,
    Map<String, List<ProjectSuggestionDto>> projectMapping, @Nullable String sonarCloudRegion) {
    super("Connected Mode suggestion for SonarQube " + (serverUrlOrOrganization.isLeft() ? "Server" : "Cloud"),
      serverUrlOrOrganization, false, true, sonarCloudRegion);
    this.projectMapping = projectMapping;
  }

  @Override
  public IStatus runInUIThread(IProgressMonitor monitor) {
    // disable the binding suggestions of all projects involved
    for (var entry : projectMapping.entrySet()) {
      for (var projectSuggestion : entry.getValue()) {
        ConfigScopeSynchronizer.disableAllBindingSuggestions(projectSuggestion.getProject());
      }
    }

    var status = super.runInUIThread(monitor);
    if (status.equals(Status.OK_STATUS)) {
      tryProjectbinding();
    }

    // (possibly) enable the binding suggestions of all projects involved, but asynchronously
    var projects = new ArrayList<ISonarLintProject>();
    for (var entry : projectMapping.entrySet()) {
      projects.addAll(entry.getValue().stream().map(state -> state.getProject()).collect(Collectors.toList()));
    }
    var job = new EnableBindingSuggestionsJob(projects);
    job.schedule(1000);

    return status;
  }

  @Override
  @Nullable
  protected ConnectionFacade createConnection(ServerConnectionModel model) {
    var wizard = new SuggestConnectionWizard(model);
    var dialog = AbstractConnectionWizard.createDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
      wizard);
    dialog.setBlockOnOpen(true);
    dialog.open();
    return wizard.getResultServer();
  }

  private void tryProjectbinding() {
    GetAllProjectsParams params;

    var token = new TokenDto(username);
    if (serverUrlOrOrganization.isLeft()) {
      params = new GetAllProjectsParams(new TransientSonarQubeConnectionDto(serverUrlOrOrganization.getLeft(),
        Either.forLeft(token)));
    } else {
      params = new GetAllProjectsParams(new TransientSonarCloudConnectionDto(serverUrlOrOrganization.getRight(),
        Either.forLeft(token), sonarCloudRegion != null ? SonarCloudRegion.valueOf(sonarCloudRegion) : SonarCloudRegion.EU));
    }

    List<SonarProjectDto> sonarProjects;
    try {
      sonarProjects = SonarLintBackendService.get()
        .getBackend()
        .getConnectionService()
        .getAllProjects(params).get().getSonarProjects();
    } catch (ExecutionException | InterruptedException err) {
      var message = "";
      if (serverUrlOrOrganization.isLeft()) {
        message += "SonarQube Server '" + serverUrlOrOrganization.getLeft() + "'";
      } else {
        message += "SonarQube Cloud organization '" + serverUrlOrOrganization.getRight() + "'";
      }

      SonarLintLogger.get().error(message + " cannot be loaded", err);
      return;
    }

    var projectKeysUnavailable = new ArrayList<String>();
    for (var entry : projectMapping.entrySet()) {
      var projectKey = entry.getKey();

      var possibleMatches = sonarProjects.stream()
        .filter(sonarProject -> sonarProject.getKey().equals(projectKey))
        .collect(Collectors.toList());
      if (possibleMatches.isEmpty()) {
        projectKeysUnavailable.add(projectKey);
        continue;
      }

      var projectSuggestions = entry.getValue();
      var projects = projectSuggestions.stream().map(state -> state.getProject()).collect(Collectors.toList());
      ProjectBindingProcess.bindProjects(connectionId, projects, projectKey);
      new ProjectBoundPopup(projectKey, projects, serverUrlOrOrganization.isRight()).open();
      invokeTelemetryAfterSuccess(projectSuggestions);
    }

    if (!projectKeysUnavailable.isEmpty()) {
      new ProjectKeyNotFoundPopup(projectKeysUnavailable, serverUrlOrOrganization).open();
    }
  }

  private static void invokeTelemetryAfterSuccess(List<ProjectSuggestionDto> projectSuggestions) {
    if (SonarLintTelemetry.isEnabled()) {
      for (var projectSuggestion : projectSuggestions) {
        if (projectSuggestion.getIsFromSharedConfiguration()) {
          SonarLintTelemetry.addedImportedBindings();
        } else {
          SonarLintTelemetry.addedAutomaticBindings();
        }
      }
    }
  }
}
