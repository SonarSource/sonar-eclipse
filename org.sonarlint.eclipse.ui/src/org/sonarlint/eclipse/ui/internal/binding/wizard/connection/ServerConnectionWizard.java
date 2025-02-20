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
package org.sonarlint.eclipse.ui.internal.binding.wizard.connection;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.DialogPage;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.PageChangingEvent;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.backend.SonarLintBackendService;
import org.sonarlint.eclipse.core.internal.engine.connected.ConnectionFacade;
import org.sonarlint.eclipse.core.internal.utils.JobUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.binding.wizard.connection.ServerConnectionModel.ConnectionType;
import org.sonarlint.eclipse.ui.internal.binding.wizard.project.ProjectBindingWizard;
import org.sonarlint.eclipse.ui.internal.util.wizard.SonarLintWizardDialog;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.check.CheckSmartNotificationsSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.org.ListUserOrganizationsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;

public class ServerConnectionWizard extends AbstractConnectionWizard {

  private final ConnectionTypeWizardPage connectionTypeWizardPage;
  private final UrlWizardPage urlPage;
  private final OrganizationWizardPage orgPage;
  private final ConnectionIdWizardPage connectionIdPage;
  private final NotificationsWizardPage notifPage;
  private final ConfirmWizardPage confirmPage;
  private final ConnectionFacade editedServer;
  private boolean redirectedAfterNotificationCheck;
  private boolean skipBindingWizard;

  private ServerConnectionWizard(String title, ServerConnectionModel model, ConnectionFacade editedServer) {
    super(title, model);
    this.editedServer = editedServer;
    connectionTypeWizardPage = new ConnectionTypeWizardPage(model);
    urlPage = new UrlWizardPage(model);
    orgPage = new OrganizationWizardPage(model);
    connectionIdPage = new ConnectionIdWizardPage(model);
    notifPage = new NotificationsWizardPage(model);
    confirmPage = new ConfirmWizardPage(model);
  }

  /**
   * Should remain public for File -> New -> SonarQube ConnectedEngineFacade
   */
  public ServerConnectionWizard() {
    this(new ServerConnectionModel());
  }

  public ServerConnectionWizard(ServerConnectionModel model) {
    this("Connect to SonarQube (Server, Cloud)", model, null);
  }

  private ServerConnectionWizard(ConnectionFacade sonarServer) {
    this(sonarServer.isSonarCloud() ? "Edit SonarQube Cloud connection" : "Edit SonarQube Server connection",
      new ServerConnectionModel(sonarServer), sonarServer);
  }

  public static WizardDialog createDialog(Shell parent) {
    return new SonarLintWizardDialog(parent, new ServerConnectionWizard());
  }

  public static WizardDialog createDialog(Shell parent, List<ISonarLintProject> selectedProjects) {
    var model = new ServerConnectionModel();
    model.setSelectedProjects(selectedProjects);
    return new SonarLintWizardDialog(parent, new ServerConnectionWizard(model));
  }

  public static WizardDialog createDialog(Shell parent, String connectionId) {
    var model = new ServerConnectionModel();
    model.setConnectionId(connectionId);
    return new SonarLintWizardDialog(parent, new ServerConnectionWizard(model));
  }

  public static WizardDialog createDialog(Shell parent, ConnectionFacade connection) {
    return new SonarLintWizardDialog(parent, new ServerConnectionWizard(connection));
  }

  @Override
  protected void actualHandlePageChanging(PageChangingEvent event) {
    var currentPage = (WizardPage) event.getCurrentPage();
    var advance = getNextPage(currentPage) == event.getTargetPage();
    if (advance && !redirectedAfterNotificationCheck && currentPage == tokenPage) {
      if (!testConnection(null)) {
        event.doit = false;
        return;
      }
      // We need to wait for credentials before testing if notifications are supported
      populateNotificationsSupported();
      // Next page depends if notifications are supported
      var newNextPage = getNextPage(currentPage);
      if (newNextPage != event.getTargetPage()) {
        // Avoid infinite recursion
        redirectedAfterNotificationCheck = true;
        getContainer().showPage(newNextPage);
        redirectedAfterNotificationCheck = false;
        event.doit = false;
        return;
      }
    }
    if (advance && event.getTargetPage() == orgPage) {
      event.doit = tryLoadOrganizations(currentPage);
      return;
    }
    if (advance && currentPage == orgPage && !testConnection(model.getOrganization())) {
      event.doit = false;
    }
  }

  @Override
  protected IWizardPage getActualStartingPage() {
    if (model.isFromConnectionSuggestion()) {
      return tokenPage;
    } else if (!model.isEdit()) {
      return connectionTypeWizardPage;
    }
    return firstPageAfterConnectionType();
  }

  @Override
  protected void actualAddPages() {
    if (!model.isEdit()) {
      addPage(connectionTypeWizardPage);
      addPage(connectionIdPage);
    }
    addPage(urlPage);
    addPage(tokenPage);
    addPage(orgPage);
    addPage(notifPage);
    addPage(confirmPage);
  }

  @Override
  protected IWizardPage getActualNextPage(IWizardPage page) {
    if (page == connectionTypeWizardPage) {
      return firstPageAfterConnectionType();
    }
    if (page == urlPage) {
      return tokenPage;
    }

    // This comes from Connection suggestion, we don't need anything from here!
    if (page == tokenPage && model.isFromConnectionSuggestion()) {
      return null;
    }

    if (page == tokenPage) {
      if (model.getConnectionType() == ConnectionType.SONARCLOUD) {
        return orgPage;
      } else {
        return afterOrgPage();
      }
    }
    if (page == orgPage) {
      return afterOrgPage();
    }
    if (page == connectionIdPage) {
      return notifPageIfSupportedOrConfirm();
    }
    if (page == notifPage) {
      return confirmPage;
    }
    return null;
  }

  @Override
  protected boolean actualCanFinish() {
    var currentPage = getContainer().getCurrentPage();
    return currentPage == confirmPage;
  }

  @Override
  protected boolean actualPerformFinish() {
    try {
      if (model.isEdit() && !testConnection(model.getOrganization())) {
        return false;
      }

      if (model.isEdit()) {
        editedServer.updateConfig(model.getServerUrl(), model.getOrganization(), model.getUsername(), model.getNotificationsDisabled());
        resultServer = editedServer;
      } else {
        finalizeConnectionCreation();
      }

      transitionToBindingWizard();
      return true;
    } catch (Exception e) {
      var currentPage = (DialogPage) getContainer().getCurrentPage();
      currentPage.setErrorMessage("Cannot create connection: " + e.getMessage());
      SonarLintLogger.get().error("Error when finishing connection wizard", e);
      return false;
    }
  }

  public void setSkipBindingWizard(boolean skipBindingWizard) {
    this.skipBindingWizard = skipBindingWizard;
  }

  private IWizardPage afterOrgPage() {
    return model.isEdit() ? notifPageIfSupportedOrConfirm() : connectionIdPage;
  }

  private IWizardPage notifPageIfSupportedOrConfirm() {
    return model.getNotificationsSupported() ? notifPage : confirmPage;
  }

  private IWizardPage firstPageAfterConnectionType() {
    // Skip URL and auth method page if SonarCloud
    return model.getConnectionType() == ConnectionType.SONARCLOUD ? tokenPage : urlPage;
  }

  private void transitionToBindingWizard() {
    var boundProjects = resultServer.getBoundProjects();
    var selectedProjects = model.getSelectedProjects();
    if (!skipBindingWizard) {
      if (selectedProjects != null && !selectedProjects.isEmpty()) {
        ProjectBindingWizard
          .createDialogSkipConnectionSelection(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), selectedProjects, resultServer)
          .open();
      } else if (boundProjects.isEmpty()) {
        ProjectBindingWizard
          .createDialogSkipConnectionSelection(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), Collections.emptyList(), resultServer)
          .open();
      }
    }
  }

  private void populateNotificationsSupported() {
    if (model.getConnectionType() == ConnectionType.SONARCLOUD) {
      model.setNotificationsSupported(true);
      return;
    }
    try {
      getContainer().run(true, false, new IRunnableWithProgress() {

        @Override
        public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
          monitor.beginTask("Check if notifications are supported", IProgressMonitor.UNKNOWN);
          try {
            var future = SonarLintBackendService.get().getBackend().getConnectionService()
              .checkSmartNotificationsSupported(new CheckSmartNotificationsSupportedParams(modelToTransientConnectionDto()));
            var response = JobUtils.waitForFutureInIRunnableWithProgress(monitor, future);

            model.setNotificationsSupported(response.isSuccess());
          } finally {
            monitor.done();
          }
        }
      });
    } catch (InvocationTargetException e) {
      SonarLintLogger.get().debug("Unable to test notifications", e.getCause());
    } catch (InterruptedException e) {
      // Nothing to do, the task was simply canceled
    }
  }

  private boolean tryLoadOrganizations(WizardPage currentPage) {
    currentPage.setMessage(null);
    try {
      getContainer().run(true, true, new IRunnableWithProgress() {

        @Override
        public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
          monitor.beginTask("Fetch organizations", IProgressMonitor.UNKNOWN);
          try {
            var future = SonarLintBackendService.get().getBackend().getConnectionService().listUserOrganizations(new ListUserOrganizationsParams(modelToCredentialDto(),
              model.getSonarCloudRegion() != null ? SonarCloudRegion.valueOf(model.getSonarCloudRegion().name()) : SonarCloudRegion.EU));
            var response = JobUtils.waitForFutureInIRunnableWithProgress(monitor, future);
            model.suggestOrganization(response.getUserOrganizations());
          } finally {
            monitor.done();
          }
        }
      });
    } catch (InvocationTargetException e) {
      SonarLintLogger.get().debug("Unable to download organizations", e.getCause());
      currentPage.setMessage(e.getCause().getMessage(), IMessageProvider.ERROR);
      return false;
    } catch (InterruptedException e) {
      return false;
    }
    return true;
  }
}
