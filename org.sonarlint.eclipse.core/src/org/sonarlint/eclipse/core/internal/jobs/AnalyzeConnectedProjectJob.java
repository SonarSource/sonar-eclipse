/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2022 SonarSource SA
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
package org.sonarlint.eclipse.core.internal.jobs;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.TriggerType;
import org.sonarlint.eclipse.core.internal.engine.connected.ConnectedEngineFacade;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintProjectConfiguration.EclipseProjectBinding;
import org.sonarlint.eclipse.core.internal.tracking.IssueTracker;
import org.sonarlint.eclipse.core.internal.tracking.ServerIssueTrackable;
import org.sonarlint.eclipse.core.internal.tracking.ServerIssueUpdater;
import org.sonarlint.eclipse.core.internal.tracking.Trackable;
import org.sonarlint.eclipse.core.internal.vcs.VcsService;
import org.sonarlint.eclipse.core.resource.ISonarLintFile;
import org.sonarlint.eclipse.core.resource.ISonarLintIssuable;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;

public class AnalyzeConnectedProjectJob extends AbstractAnalyzeProjectJob<ConnectedAnalysisConfiguration> {

  private final EclipseProjectBinding binding;
  private final ConnectedEngineFacade engineFacade;

  public AnalyzeConnectedProjectJob(AnalyzeProjectRequest request, EclipseProjectBinding binding, ConnectedEngineFacade engineFacade) {
    super(request);
    this.binding = binding;
    this.engineFacade = engineFacade;
  }

  @Override
  protected ConnectedAnalysisConfiguration prepareAnalysisConfig(Path projectBaseDir, List<ClientInputFile> inputFiles, Map<String, String> mergedExtraProps) {
    SonarLintLogger.get().debug("Connected mode (using configuration of '" + binding.projectKey() + "' in connection '" + binding.connectionId() + "')");
    return ConnectedAnalysisConfiguration.builder()
      .setProjectKey(binding.projectKey())
      .setBaseDir(projectBaseDir)
      .addInputFiles(inputFiles)
      .putAllExtraProperties(mergedExtraProps)
      .build();
  }

  @Override
  protected AnalysisResults runAnalysis(ConnectedAnalysisConfiguration analysisConfig, SonarLintIssueListener issueListener, IProgressMonitor monitor) {
    return engineFacade.runAnalysis(analysisConfig, issueListener, monitor);
  }

  @Override
  protected void trackIssues(Map<ISonarLintFile, IDocument> docPerFile, Map<ISonarLintIssuable, List<Issue>> rawIssuesPerResource, TriggerType triggerType,
    IProgressMonitor monitor) {
    if (triggerType.shouldUpdateProjectIssuesSync(rawIssuesPerResource.size())) {
      SonarLintLogger.get().debug("Download engineFacade issues for project " + getProject().getName());
      engineFacade.downloadServerIssues(binding.projectKey(), VcsService.getServerBranch(getProject()), monitor);
    }
    super.trackIssues(docPerFile, rawIssuesPerResource, triggerType, monitor);
    if (triggerType.shouldUpdateFileIssuesAsync()) {
      trackServerIssuesAsync(engineFacade, rawIssuesPerResource.keySet(), docPerFile, triggerType);
    }
  }

  @Override
  protected Collection<Trackable> trackFileIssues(ISonarLintFile file, List<Trackable> trackables, IssueTracker issueTracker, TriggerType triggerType, int totalTrackedFiles,
    IProgressMonitor monitor) {
    var tracked = super.trackFileIssues(file, trackables, issueTracker, triggerType, totalTrackedFiles, monitor);
    if (!tracked.isEmpty()) {
      tracked = trackServerIssuesSync(engineFacade, file, tracked, triggerType.shouldUpdateFileIssuesSync(totalTrackedFiles), monitor);
    }
    return tracked;

  }

  private void trackServerIssuesAsync(ConnectedEngineFacade engineFacade, Collection<ISonarLintIssuable> resources, Map<ISonarLintFile, IDocument> docPerFile,
    TriggerType triggerType) {
    SonarLintCorePlugin.getInstance().getServerIssueUpdater().updateAsync(engineFacade, getProject(),
      binding,
      resources,
      docPerFile, triggerType);
  }

  private Collection<Trackable> trackServerIssuesSync(ConnectedEngineFacade engineFacade, ISonarLintFile file, Collection<Trackable> tracked, boolean updateServerIssues,
    IProgressMonitor monitor) {
    List<ServerIssue> serverIssues;
    if (updateServerIssues) {
      serverIssues = ServerIssueUpdater.fetchServerIssues(engineFacade, binding, VcsService.getServerBranch(getProject()), file, monitor);
    } else {
      serverIssues = engineFacade.getServerIssues(binding, file.getProjectRelativePath());
    }
    Collection<Trackable> serverIssuesTrackable = serverIssues.stream().map(ServerIssueTrackable::new).collect(Collectors.toList());
    return IssueTracker.matchAndTrackServerIssues(serverIssuesTrackable, tracked);
  }

}
