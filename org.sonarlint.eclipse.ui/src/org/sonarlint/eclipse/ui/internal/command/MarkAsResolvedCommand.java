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
package org.sonarlint.eclipse.ui.internal.command;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Optional;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.backend.SonarLintBackendService;
import org.sonarlint.eclipse.core.internal.engine.connected.ResolvedBinding;
import org.sonarlint.eclipse.core.internal.markers.MarkerUtils;
import org.sonarlint.eclipse.core.internal.utils.JobUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.SonarLintImages;
import org.sonarlint.eclipse.ui.internal.dialog.MarkAsResolvedDialog;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ChangeIssueStatusParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedResponse;


/**
 *  Command invoked on issues matched from SonarQube / SonarCloud to resolve an issue based on server configuration
 *  and user authentication. Not available on issues only found locally!
 */
public class MarkAsResolvedCommand extends AbstractIssueCommand implements IElementUpdater {
  @Override
  public void updateElement(UIElement element, Map parameters) {
    var window = element.getServiceLocator().getService(IWorkbenchWindow.class);
    if (window == null) {
      return;
    }
    var selection = (IStructuredSelection) window.getSelectionService().getSelection();
    var binding = getBinding(getSelectedMarker(selection));
    if (binding.isPresent()) {
      element.setIcon(binding.get().getEngineFacade().isSonarCloud() ? SonarLintImages.SONARCLOUD_16 : SonarLintImages.SONARQUBE_16);
    }
  }

  /** Check for issue binding: Either SonarQube or SonarCloud */
  private static Optional<ResolvedBinding> getBinding(IMarker marker) {
    var project = Adapters.adapt(marker.getResource().getProject(), ISonarLintProject.class);
    return SonarLintCorePlugin.getServersManager().resolveBinding(project);
  }

  @Override
  protected void execute(IMarker selectedMarker, IWorkbenchWindow window) {
    var issueKey = selectedMarker.getAttribute(MarkerUtils.SONAR_MARKER_SERVER_ISSUE_KEY_ATTR, null);
    if (issueKey == null) {
      window.getShell().getDisplay()
      .asyncExec(() -> MessageDialog.openError(window.getShell(), "Mark Issue as Resolved", "No issue key available"));
      return;
    }
    
    String markerType;
    try {
      markerType = selectedMarker.getType();
    } catch (CoreException err) {
      window.getShell().getDisplay()
      .asyncExec(() -> MessageDialog.openError(window.getShell(), "Mark Issue as Resolved, marker type not available",
        err.getMessage()));
      return;
    }
    
    var checkJob = new Job("Check user permissions for setting the issue resolution") {
      private CheckStatusChangePermittedResponse result;
      private ResolvedBinding resolvedBinding;

      @Override
      protected IStatus run(IProgressMonitor monitor) {
        var binding = getBinding(selectedMarker);
        if (binding.isPresent()) {
          resolvedBinding = binding.get();
          try {
            result = JobUtils.waitForFuture(monitor, SonarLintBackendService.get().getBackend().getIssueService()
              .checkStatusChangePermitted(new CheckStatusChangePermittedParams(resolvedBinding.getProjectBinding().connectionId(), issueKey)));
            return Status.OK_STATUS;
          } catch (InvocationTargetException e) {
            return new Status(IStatus.ERROR, SonarLintCorePlugin.PLUGIN_ID, e.getMessage(), e);
          } catch (InterruptedException e) {
            return new Status(IStatus.CANCEL, SonarLintCorePlugin.PLUGIN_ID, e.getMessage(), e);
          }

        } else {
          return Status.error("Project is not bound anymore");
        }
      }

    };

    JobUtils.scheduleAfterSuccess(checkJob, () -> afterCheckSuccessful(issueKey, markerType, checkJob.result,
      checkJob.resolvedBinding, window));
    checkJob.schedule();
  }

  private static void afterCheckSuccessful(String markerId, String markerType, CheckStatusChangePermittedResponse result,
    ResolvedBinding resolvedBinding, IWorkbenchWindow window) {
    var hostURL = resolvedBinding.getEngineFacade().getHost();
    var isSonarCloud = resolvedBinding.getEngineFacade().isSonarCloud();
    
    if (!result.isPermitted()) {
      window.getShell().getDisplay()
        .asyncExec(() -> MessageDialog.openError(window.getShell(), "Mark Issue as Resolved on " + (isSonarCloud ? "SonarCloud" : "SonarQube"),
          result.getNotPermittedReason()));
      return;
    }
    
    window.getShell().getDisplay().asyncExec(() -> {
      var dialog = new MarkAsResolvedDialog(window.getShell(), result.getAllowedStatuses(), hostURL, isSonarCloud);
      if (dialog.open() == Window.OK) {
        var issueService = SonarLintBackendService.get().getBackend().getIssueService();
        
        // issue status transition
        var newStatus = dialog.getFinalTransition();
        issueService.changeStatus(new ChangeIssueStatusParams(resolvedBinding.getEngineFacade().getId(),
          markerId,
          newStatus,
          markerType.equals(SonarLintCorePlugin.MARKER_TAINT_ID)));
        
        // (optional) issue status comment
        var comment = dialog.getFinalComment();
        if (!comment.isBlank()) {
          issueService.addComment(new AddIssueCommentParams(resolvedBinding.getEngineFacade().getId(),
            markerId,
            comment));
        }
      }
    });
  }
}
