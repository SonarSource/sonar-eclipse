/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2023 SonarSource SA
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

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.TriggerType;
import org.sonarlint.eclipse.core.internal.jobs.AnalyzeProjectRequest.FileWithDocument;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarsource.sonarlint.core.commons.Language;

public class AnalyzeProjectsJob extends WorkspaceJob {
  private static final String UNABLE_TO_ANALYZE_FILES = "Unable to analyze files";
  private final Map<ISonarLintProject, Collection<FileWithDocument>> filesPerProject;
  private final EnumSet<Language> unavailableLanguagesReference;
  
  /** Sometimes we trigger an analysis but don't care about unsupported languages, e.g. already in connected mode */
  public AnalyzeProjectsJob(Map<ISonarLintProject, Collection<FileWithDocument>> filesPerProject) {
    this(filesPerProject, EnumSet.noneOf(Language.class));
  }

  public AnalyzeProjectsJob(Map<ISonarLintProject, Collection<FileWithDocument>> filesPerProject,
    EnumSet<Language> unavailableLanguagesReference) {
    super("Analyze all files");
    this.filesPerProject = filesPerProject;
    setPriority(Job.LONG);
    this.unavailableLanguagesReference = unavailableLanguagesReference;
  }

  @Override
  public IStatus runInWorkspace(IProgressMonitor monitor) {
    var global = SubMonitor.convert(monitor, 100);
    try {
      global.setTaskName("Analysis");
      SonarLintMarkerUpdater.deleteAllMarkersFromReport();
      var analysisMonitor = SubMonitor.convert(global.newChild(100), filesPerProject.size());
      for (var entry : filesPerProject.entrySet()) {
        if (monitor.isCanceled()) {
          return Status.CANCEL_STATUS;
        }
        var project = entry.getKey();
        if (!project.isOpen()) {
          analysisMonitor.worked(1);
          continue;
        }
        global.setTaskName("Analyzing project " + project.getName());
        var req = new AnalyzeProjectRequest(project, entry.getValue(), TriggerType.MANUAL);
        var job = AbstractAnalyzeProjectJob.create(req, unavailableLanguagesReference);
        var subMonitor = analysisMonitor.newChild(1);
        job.run(subMonitor);
        subMonitor.done();
      }

    } catch (Exception e) {
      SonarLintLogger.get().error(UNABLE_TO_ANALYZE_FILES, e);
      return new Status(Status.ERROR, SonarLintCorePlugin.PLUGIN_ID, UNABLE_TO_ANALYZE_FILES, e);
    }
    return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
  }

  @Override
  public final boolean belongsTo(Object family) {
    return "org.sonarlint.eclipse.projectsJob".equals(family);
  }

}
