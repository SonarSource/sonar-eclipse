/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2024 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal.backend;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.swt.widgets.Display;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.TriggerType;
import org.sonarlint.eclipse.core.internal.backend.ConfigScopeSynchronizer;
import org.sonarlint.eclipse.core.internal.backend.SonarLintEclipseHeadlessRpcClient;
import org.sonarlint.eclipse.core.internal.jobs.AbstractAnalyzeProjectJob;
import org.sonarlint.eclipse.core.internal.jobs.TaintIssuesMarkerUpdateJob;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintGlobalConfiguration;
import org.sonarlint.eclipse.core.internal.utils.SonarLintUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.binding.actions.AnalysisJobsScheduler;
import org.sonarlint.eclipse.ui.internal.binding.assist.AbstractAssistCreatingConnectionJob;
import org.sonarlint.eclipse.ui.internal.binding.assist.AssistBindingJob;
import org.sonarlint.eclipse.ui.internal.binding.assist.AssistCreatingAutomaticConnectionJob;
import org.sonarlint.eclipse.ui.internal.binding.assist.AssistCreatingManualConnectionJob;
import org.sonarlint.eclipse.ui.internal.binding.wizard.project.ProjectBindingProcess;
import org.sonarlint.eclipse.ui.internal.job.BackendProgressJobScheduler;
import org.sonarlint.eclipse.ui.internal.job.OpenIssueInEclipseJob;
import org.sonarlint.eclipse.ui.internal.job.OpenIssueInEclipseJob.OpenIssueContext;
import org.sonarlint.eclipse.ui.internal.popup.BindingSuggestionPopup;
import org.sonarlint.eclipse.ui.internal.popup.DeveloperNotificationPopup;
import org.sonarlint.eclipse.ui.internal.popup.MessagePopup;
import org.sonarlint.eclipse.ui.internal.popup.NoBindingSuggestionFoundPopup;
import org.sonarlint.eclipse.ui.internal.popup.SingleBindingSuggestionPopup;
import org.sonarlint.eclipse.ui.internal.popup.SoonUnsupportedPopup;
import org.sonarlint.eclipse.ui.internal.popup.SuggestConnectionPopup;
import org.sonarlint.eclipse.ui.internal.popup.SuggestMultipleConnectionsPopup;
import org.sonarlint.eclipse.ui.internal.util.BrowserUtils;
import org.sonarlint.eclipse.ui.internal.util.PlatformUtils;
import org.sonarsource.sonarlint.core.rpc.client.SonarLintCancelChecker;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.TaintVulnerabilityDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.binding.AssistBindingResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.AssistCreatingConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.hotspot.HotspotDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.IssueDetailsDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowSoonUnsupportedMessageParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.ReportProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.progress.StartProgressParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

public class SonarLintEclipseRpcClient extends SonarLintEclipseHeadlessRpcClient {

  @Override
  public void openUrlInBrowser(URL url) {
    BrowserUtils.openExternalBrowser(url, Display.getDefault());
  }

  @Override
  public void suggestBinding(Map<String, List<BindingSuggestionDto>> suggestionsByConfigScope) {
    Map<ISonarLintProject, List<BindingSuggestionDto>> suggestionsByProject = new HashMap<>();
    suggestionsByConfigScope.forEach((configScopeId, suggestions) -> {
      var projectOpt = SonarLintUtils.tryResolveProject(configScopeId);
      if (projectOpt.isPresent()) {
        // Everything can happen asynchronously on Sloop, therefore there might be a late binding suggestion coming
        // in for a project that is already bound and configured locally and is currently "updated" in Sloop in a
        // different thread!
        var project = projectOpt.get();
        if (project.isOpen() && !ProjectBindingProcess.isProjectBound(project)) {
          suggestionsByProject.put(projectOpt.get(), suggestions);
        }
      }
    });

    if (!suggestionsByProject.isEmpty()) {
      var firstProjectSuggestions = suggestionsByProject.values().iterator().next();
      if (suggestionsByProject.values().stream().allMatch(s -> areAllSameSuggestion(s, firstProjectSuggestions))) {
        // Suggest binding all projects to the suggestion
        Display.getDefault().asyncExec(() -> {
          var popup = new SingleBindingSuggestionPopup(List.copyOf(suggestionsByProject.keySet()), firstProjectSuggestions.get(0));
          popup.open();
        });
      } else {
        // Suggest binding all projects but let the user choose
        Display.getDefault().asyncExec(() -> {
          var popup = new BindingSuggestionPopup(List.copyOf(suggestionsByProject.keySet()));
          popup.open();
        });
      }
    }
  }

  private static boolean areAllSameSuggestion(List<BindingSuggestionDto> otherSuggestions, List<BindingSuggestionDto> firstProjectSuggestions) {
    if (otherSuggestions.size() != 1 || firstProjectSuggestions.size() != 1) {
      return false;
    }
    var suggestionDto = otherSuggestions.get(0);
    var firstSuggestion = firstProjectSuggestions.get(0);
    return Objects.equals(suggestionDto.getConnectionId(), firstSuggestion.getConnectionId())
      && Objects.equals(suggestionDto.getSonarProjectKey(), firstSuggestion.getSonarProjectKey());
  }

  @Override
  public void showMessage(MessageType type, String text) {
    showPopup(text);
  }

  @Override
  public String getClientLiveDescription() {
    return ResourcesPlugin.getWorkspace().getRoot().getLocation().lastSegment();
  }

  @Override
  public void showHotspot(String configurationScopeId, HotspotDetailsDto hotspotDetails) {
    SonarLintUtils.tryResolveProject(configurationScopeId)
      .ifPresent(project -> new ShowHotspotJob(project, hotspotDetails).schedule());
  }

  @Override
  public AssistCreatingConnectionResponse assistCreatingConnection(AssistCreatingConnectionParams params, SonarLintCancelChecker cancelChecker) throws CancellationException {
    var baseUrl = params.getServerUrl();

    try {
      AbstractAssistCreatingConnectionJob job;

      SonarLintLogger.get().debug("Assist creating a new connection...");
      if (params.getTokenName() != null && params.getTokenValue() != null) {
        job = new AssistCreatingAutomaticConnectionJob(Either.forLeft(baseUrl), params.getTokenValue());
      } else {
        job = new AssistCreatingManualConnectionJob(Either.forLeft(baseUrl));
      }

      job.schedule();
      job.join();
      if (job.getResult().isOK()) {
        SonarLintLogger.get().debug("Successfully created connection '" + job.getConnectionId() + "'");
        return new AssistCreatingConnectionResponse(job.getConnectionId());
      } else if (job.getResult().matches(IStatus.CANCEL)) {
        SonarLintLogger.get().debug("Assist creating connection was cancelled.");
      }
      throw new IllegalStateException(job.getResult().getMessage(), job.getResult().getException());
    } catch (InterruptedException e) {
      SonarLintLogger.get().debug("Assist creating connection was interrupted.");
      throw new CancellationException("Interrupted!");
    }
  }

  @Override
  public void noBindingSuggestionFound(String projectKey) {
    NoBindingSuggestionFoundPopup.displayPopupIfNotIgnored(projectKey);
  }

  @Override
  public AssistBindingResponse assistBinding(AssistBindingParams params, SonarLintCancelChecker cancelChecker) throws CancellationException {
    try {
      SonarLintLogger.get().debug("Assist creating a new binding...");
      var job = new AssistBindingJob(params.getConnectionId(), params.getProjectKey());
      job.schedule();
      job.join();
      if (job.getResult().isOK()) {
        SonarLintLogger.get().debug("Successfully created binding");
        return new AssistBindingResponse(ConfigScopeSynchronizer.getConfigScopeId(job.getProject()));
      }
      throw new IllegalStateException(job.getResult().getMessage(), job.getResult().getException());
    } catch (InterruptedException e) {
      throw new CancellationException("Interrupted!");
    }
  }

  private static void showPopup(String message) {
    Display.getDefault().asyncExec(() -> {
      var popup = new MessagePopup(message);
      popup.open();
    });
  }

  @Override
  public void showSmartNotification(ShowSmartNotificationParams params) {
    var connectionOpt = SonarLintCorePlugin.getConnectionManager().findById(params.getConnectionId());
    if (connectionOpt.isEmpty()) {
      return;
    }

    Display.getDefault().asyncExec(() -> new DeveloperNotificationPopup(connectionOpt.get(), params, connectionOpt.get().isSonarCloud()).open());
  }

  /** Start IDE progress bar for backend jobs running out of IDE scope */
  @Override
  public void startProgress(StartProgressParams params) throws UnsupportedOperationException {
    BackendProgressJobScheduler.get().startProgress(params);
  }

  /** Update / finish IDE progress bar for backend jobs running out of IDE scope */
  @Override
  public void reportProgress(ReportProgressParams params) {
    if (params.getNotification().isLeft()) {
      BackendProgressJobScheduler.get().update(params.getTaskId(), params.getNotification().getLeft());
    } else {
      BackendProgressJobScheduler.get().complete(params.getTaskId());
    }
  }

  /**
   *  After synchronization of the backend, the taint vulnerabilities view should be updated with the remote findings
   *  and also an analysis of the opened files should be triggered.
   */
  @Override
  public void didSynchronizeConfigurationScopes(Set<String> configurationScopeIds) {
    super.didSynchronizeConfigurationScopes(configurationScopeIds);
    configurationScopeIds.stream()
      .map(SonarLintUtils::tryResolveProject)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .distinct()
      .forEach(project -> {
        var openedFiles = PlatformUtils.collectOpenedFiles(project, f -> true);
        if (!openedFiles.isEmpty() && openedFiles.containsKey(project)) {
          var bindingOpt = SonarLintCorePlugin.getConnectionManager().resolveBinding(project);
          if (bindingOpt.isPresent()) {
            var connection = bindingOpt.get().getConnectionFacade();

            // present taint vulnerabilities without re-fetching them from the server
            var files = openedFiles.get(project).stream()
              .map(file -> file.getFile())
              .collect(Collectors.toList());
            new TaintIssuesMarkerUpdateJob(connection, project, files).schedule();

            // also schedule analyze of opened files based on synced information
            AnalysisJobsScheduler.scheduleAnalysisOfOpenFiles(project, TriggerType.BINDING_CHANGE,
              f -> SonarLintUtils.isBoundToConnection(f, connection), false);
          }
        }
      });
  }

  @Override
  public void showSoonUnsupportedMessage(ShowSoonUnsupportedMessageParams params) {
    var connectionVersionCombination = params.getDoNotShowAgainId();
    if (SonarLintGlobalConfiguration.alreadySoonUnsupportedConnection(connectionVersionCombination)) {
      return;
    }

    Display.getDefault().syncExec(() -> {
      var popup = new SoonUnsupportedPopup(params.getDoNotShowAgainId(), params.getText());
      popup.setFadingEnabled(false);
      popup.setDelayClose(0L);
      popup.open();
    });
  }

  /**
   *  Before this method is even invoked, some magic already interacts with Eclipse: When there is no connection to
   *  SonarQube, it will automatically launch the "Connect to SonarQube or SonarCloud" wizard. Some magic will also ask
   *  the user to find the correct project if there is currently no binding to the SonarQube project.
   *  -> both cases must not be tested afterwards
   */
  @Override
  public void showIssue(String configScopeId, IssueDetailsDto issueDetails) {
    // We were just asked to find the correct project, this cannot happen -> Only log the information
    var projectOpt = SonarLintUtils.tryResolveProject(configScopeId);
    if (projectOpt.isEmpty()) {
      SonarLintLogger.get().error("Open in IDE: The project '" + configScopeId + "' is not found in the workspace");
      return;
    }
    var project = projectOpt.get();

    // We were just asked to connect and create a binding, this cannot happen -> Only log the information
    var bindingOpt = SonarLintCorePlugin.getConnectionManager().resolveBinding(project);
    if (bindingOpt.isEmpty()) {
      SonarLintLogger.get().error("Open in IDE: The project '" + configScopeId
        + "' removed its binding in the middle of running the showIssue action on '" + issueDetails.getIssueKey() + "'");
      return;
    }

    // Handle expensive checks and actual logic in separate job to not block the thread
    new OpenIssueInEclipseJob(new OpenIssueContext("Open in IDE", issueDetails, project, bindingOpt.get()))
      .schedule();
  }

  @Override
  public void didChangeTaintVulnerabilities(String configurationScopeId, Set<UUID> closedTaintVulnerabilityIds, List<TaintVulnerabilityDto> addedTaintVulnerabilities,
    List<TaintVulnerabilityDto> updatedTaintVulnerabilities) {
    var projectOpt = SonarLintUtils.tryResolveProject(configurationScopeId);
    if (projectOpt.isEmpty()) {
      return;
    }
    var project = projectOpt.get();
    var bindingOpt = SonarLintCorePlugin.getConnectionManager().resolveBinding(project);
    if (bindingOpt.isPresent()) {
      var openedFiles = PlatformUtils.collectOpenedFiles(project, f -> true);
      var files = openedFiles.get(project).stream()
        .map(file -> file.getFile())
        .collect(Collectors.toList());

      new TaintIssuesMarkerUpdateJob(bindingOpt.get().getConnectionFacade(), project, files).schedule();
    }
  }

  @Override
  public void log(LogParams params) {
    switch (params.getLevel()) {
      case TRACE:
      case DEBUG:
        SonarLintLogger.get().debug(params.getMessage());
        break;
      case ERROR:
        SonarLintLogger.get().error(params.getMessage());
        break;
      default:
        SonarLintLogger.get().info(params.getMessage());
    }
  }

  @Override
  public void didChangeAnalysisReadiness(Set<String> configurationScopeIds, boolean areReadyForAnalysis) {
    var projects = configurationScopeIds.stream()
      .map(SonarLintUtils::tryResolveProject)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());

    // In order for use to simplify listening to changes in ITs we log the information per configuration scope id!
    for (var project : projects) {
      SonarLintLogger.get().debug("Project at '" + project.getName()
        + "' changed ready status for analysis to: " + areReadyForAnalysis);
    }

    AbstractAnalyzeProjectJob.changeAnalysisReadiness(configurationScopeIds, areReadyForAnalysis);
    if (areReadyForAnalysis) {
      AnalysisJobsScheduler.scheduleAnalysisOfOpenFiles(projects, TriggerType.ANALYSIS_READY, false);
    }
  }

  /**
   *  This handles the connection suggestions of the shared Connected Mode. As Eclipse can have all kinds of different
   *  combinations we want to reduce the number of notifications to the user and therefore aggregate as much
   *  information as possible.
   */
  @Override
  public void suggestConnection(Map<String, List<ConnectionSuggestionDto>> suggestionsByConfigScope) {
    if (SonarLintGlobalConfiguration.noConnectionSuggestions()) {
      return;
    }

    var organizationBasedConnections = new HashMap<String, HashMap<String, List<ISonarLintProject>>>();
    var serverUrlBasedConnections = new HashMap<String, HashMap<String, List<ISonarLintProject>>>();
    var multipleSuggestions = new HashMap<ISonarLintProject, List<ConnectionSuggestionDto>>();

    for (var entry : suggestionsByConfigScope.entrySet()) {
      var configScopeId = entry.getKey();
      var projectOpt = SonarLintUtils.tryResolveProject(configScopeId);
      if (projectOpt.isEmpty()) {
        SonarLintLogger.get().debug("No project can be found for '" + configScopeId + "' for a Connection Suggestion!");
        continue;
      }
      var project = projectOpt.get();

      var suggestions = entry.getValue();
      if (suggestions.size() > 1) {
        multipleSuggestions.put(project, suggestions);
        continue;
      }

      var eitherSuggestion = suggestions.get(0).getConnectionSuggestion();
      if (eitherSuggestion.isLeft()) {
        var suggestion = eitherSuggestion.getLeft();
        var serverUrl = suggestion.getServerUrl();
        var projectKey = suggestion.getProjectKey();

        serverUrlBasedConnections.putIfAbsent(serverUrl, new HashMap<>());

        var sonarProjects = serverUrlBasedConnections.get(serverUrl);
        sonarProjects.putIfAbsent(projectKey, new ArrayList<>());

        var projects = sonarProjects.get(projectKey);
        projects.add(project);
      } else {
        var suggestion = eitherSuggestion.getRight();
        var organization = suggestion.getOrganization();
        var projectKey = suggestion.getProjectKey();

        organizationBasedConnections.putIfAbsent(organization, new HashMap<>());

        var sonarProjects = organizationBasedConnections.get(organization);
        sonarProjects.putIfAbsent(projectKey, new ArrayList<>());

        var projects = sonarProjects.get(projectKey);
        projects.add(project);
      }
    }

    Display.getDefault().asyncExec(() -> {
      // for all SonarCloud organizations display one notification each (so only one connection creation will be done
      // per organization)
      for (var entry : organizationBasedConnections.entrySet()) {
        var dialog = new SuggestConnectionPopup(Either.forRight(entry.getKey()), entry.getValue());
        dialog.open();
      }

      // for all SonarQube URLs display one notification each (so only one connection creation will be done per URL)
      for (var entry : serverUrlBasedConnections.entrySet()) {
        var dialog = new SuggestConnectionPopup(Either.forLeft(entry.getKey()), entry.getValue());
        dialog.open();
      }

      // for all (complicated) leftover projects display a specific notification each
      for (var entry : multipleSuggestions.entrySet()) {
        var dialog = new SuggestMultipleConnectionsPopup(entry.getKey(), entry.getValue());
        dialog.open();
      }
    });
  }
}
