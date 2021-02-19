/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2020 SonarSource SA
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

import java.util.List;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.sonarlint.eclipse.core.internal.engine.connected.IConnectedEngineFacade;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.SonarLintUiPlugin;
import org.sonarlint.eclipse.ui.internal.job.SubscribeToNotificationsJob;
import org.sonarlint.eclipse.ui.internal.util.wizard.SonarLintWizardDialog;

public class EditNotificationsWizard extends Wizard implements INewWizard {

  private final ServerConnectionModel model;
  private final NotificationsWizardPage notifPage;
  private final IConnectedEngineFacade editedServer;

  private EditNotificationsWizard(String title, ServerConnectionModel model, IConnectedEngineFacade editedServer) {
    super();
    this.model = model;
    // Assume that if we open this wizard, notifications are supported
    model.setNotificationsSupported(true);
    this.editedServer = editedServer;
    setNeedsProgressMonitor(true);
    setWindowTitle(title);
    setHelpAvailable(false);
    notifPage = new NotificationsWizardPage(model);
  }

  private EditNotificationsWizard(IConnectedEngineFacade sonarServer) {
    this(sonarServer.isSonarCloud() ? "Edit SonarCloud notifications" : "Edit SonarQube notifications", new ServerConnectionModel(sonarServer), sonarServer);
  }

  public static WizardDialog createDialog(Shell parent, IConnectedEngineFacade sonarServer) {
    return new SonarLintWizardDialog(parent, new EditNotificationsWizard(sonarServer));
  }

  @Override
  public void init(IWorkbench workbench, IStructuredSelection selection) {
    // Nothing to do
  }

  @Override
  public void addPages() {
    addPage(notifPage);
  }

  @Override
  public IWizardPage getPreviousPage(IWizardPage page) {
    // This method is only used for the first page of a wizard,
    // because every following page remember the previous one on its own
    return null;
  }

  @Override
  public boolean performFinish() {
    editedServer.updateConfig(model.getServerUrl(), model.getOrganization(), model.getUsername(), model.getPassword(), model.getNotificationsDisabled());

    List<ISonarLintProject> boundProjects = editedServer.getBoundProjects();
    if (model.getNotificationsSupported() && !model.getNotificationsDisabled() && !boundProjects.isEmpty()) {
      new SubscribeToNotificationsJob(boundProjects).schedule();
    } else {
      boundProjects.forEach(SonarLintUiPlugin::unsubscribeToNotifications);
    }
    return true;
  }

}
