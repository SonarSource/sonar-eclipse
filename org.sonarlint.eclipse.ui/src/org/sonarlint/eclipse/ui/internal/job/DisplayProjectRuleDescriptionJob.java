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
package org.sonarlint.eclipse.ui.internal.job;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.widgets.Display;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.backend.SonarLintBackendService;
import org.sonarlint.eclipse.core.internal.jobs.AbstractSonarProjectJob;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.rule.RuleDetailsPanel;

/** Update "web browser" view for the project rule description (maybe context based on connection) in a separate thread */
public class DisplayProjectRuleDescriptionJob extends AbstractSonarProjectJob {
  private final ISonarLintProject project;
  private final String ruleKey;
  private final String contextKey;
  private final RuleDetailsPanel ruleDetailsPanel;

  public DisplayProjectRuleDescriptionJob(ISonarLintProject project, String ruleKey, @Nullable String contextKey, RuleDetailsPanel ruleDetailsPanel) {
    super("Fetching rule description for rule '" + ruleKey + "'...", project);
    this.project = project;
    this.ruleKey = ruleKey;
    this.contextKey = contextKey;
    this.ruleDetailsPanel = ruleDetailsPanel;
  }

  @Override
  protected IStatus doRun(IProgressMonitor monitor) throws CoreException {
    try {
      // Getting the CompletableFuture<...> object before running the UI update to not block the UI thread
      var ruleDetails = SonarLintBackendService.get().getEffectiveRuleDetails(project, ruleKey, contextKey);
      //Display.getDefault().syncExec(() -> ruleDetailsPanel.updateRule(ruleDetails));
      Display.getDefault().syncExec(() -> ruleDetailsPanel.updateRule(null));
    } catch (Exception e) {
      SonarLintLogger.get().error("Unable to display project rule description for rule " + ruleKey, e);
      Display.getDefault().syncExec(ruleDetailsPanel::clearRule);
    }

    return Status.OK_STATUS;
  }
}
