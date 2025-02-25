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
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.IPageChangingListener;
import org.eclipse.jface.dialogs.PageChangingEvent;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.backend.SonarLintBackendService;
import org.sonarlint.eclipse.core.internal.engine.connected.ConnectionFacade;
import org.sonarlint.eclipse.core.internal.utils.JobUtils;
import org.sonarlint.eclipse.ui.internal.Messages;
import org.sonarlint.eclipse.ui.internal.binding.BindingsView;
import org.sonarlint.eclipse.ui.internal.binding.wizard.connection.ServerConnectionModel.ConnectionType;
import org.sonarlint.eclipse.ui.internal.util.wizard.SonarLintWizardDialog;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.UsernamePasswordDto;

/**
 *  We have two different wizards for connections: One for manually creating / editing a connection and one for the
 *  connection suggestions provided by shared Connected Mode configurations!
 */
public abstract class AbstractConnectionWizard extends Wizard implements INewWizard, IPageChangingListener {
  protected final ServerConnectionModel model;
  protected final TokenWizardPage tokenPage;
  protected ConnectionFacade resultServer;

  protected AbstractConnectionWizard(String title, ServerConnectionModel model) {
    super();
    this.model = model;
    setNeedsProgressMonitor(true);
    setWindowTitle(title);
    setHelpAvailable(false);
    tokenPage = new TokenWizardPage(model);
  }

  public static WizardDialog createDialog(Shell parent, AbstractConnectionWizard wizard) {
    return new SonarLintWizardDialog(parent, wizard);
  }

  @Override
  public final void init(IWorkbench workbench, IStructuredSelection selection) {
    // Nothing to do
  }

  @Override
  public final IWizardPage getPreviousPage(IWizardPage page) {
    // This method is only used for the first page of a wizard,
    // because every following page remember the previous one on its own
    return null;
  }

  @Override
  public final void handlePageChanging(PageChangingEvent event) {
    actualHandlePageChanging(event);
  }

  @Override
  public final IWizardPage getStartingPage() {
    return getActualStartingPage();
  }

  @Override
  public final void addPages() {
    actualAddPages();
  }

  @Override
  public final IWizardPage getNextPage(IWizardPage page) {
    return getActualNextPage(page);
  }

  @Override
  public final boolean canFinish() {
    return actualCanFinish();
  }

  @Override
  public final boolean performFinish() {
    return actualPerformFinish();
  }

  protected abstract void actualHandlePageChanging(PageChangingEvent event);

  protected abstract IWizardPage getActualStartingPage();

  protected abstract void actualAddPages();

  protected abstract IWizardPage getActualNextPage(IWizardPage page);

  protected abstract boolean actualCanFinish();

  protected abstract boolean actualPerformFinish();

  protected void finalizeConnectionCreation() {
    resultServer = SonarLintCorePlugin.getConnectionManager().create(model.getConnectionId(), model.getServerUrl(),
      model.getOrganization(), model.getSonarCloudRegion().name(), model.getUsername(), model.getNotificationsDisabled());
    SonarLintCorePlugin.getConnectionManager().addConnection(resultServer, model.getUsername());
    try {
      PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(BindingsView.ID);
    } catch (PartInitException e) {
      SonarLintLogger.get().error("Unable to open SonarLint bindings view", e);
    }
  }

  public final ConnectionFacade getResultServer() {
    return resultServer;
  }

  protected boolean testConnection(@Nullable String organization) {
    var currentPage = getContainer().getCurrentPage();
    var response = new AtomicReference<ValidateConnectionResponse>();
    try {
      getContainer().run(true, true, new IRunnableWithProgress() {

        @Override
        public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
          monitor.beginTask(organization == null ? "Testing connection" : "Testing access to the organization", IProgressMonitor.UNKNOWN);
          try {
            var params = new ValidateConnectionParams(modelToTransientConnectionDto());
            var future = SonarLintBackendService.get().getBackend().getConnectionService().validateConnection(params);
            response.set(JobUtils.waitForFutureInIRunnableWithProgress(monitor, future));
          } finally {
            monitor.done();
          }
        }
      });
    } catch (InterruptedException canceled) {
      ((WizardPage) currentPage).setMessage(null, IMessageProvider.NONE);
      return false;
    } catch (InvocationTargetException e) {
      SonarLintLogger.get().error(message(e), e);
      ((WizardPage) currentPage).setMessage(Messages.ServerLocationWizardPage_msg_error + " " + message(e), IMessageProvider.ERROR);
      return false;
    }

    if (!response.get().isSuccess()) {
      ((WizardPage) currentPage).setMessage(response.get().getMessage(), IMessageProvider.ERROR);
      return false;
    } else {
      ((WizardPage) currentPage).setMessage("Successfully connected!", IMessageProvider.INFORMATION);
      return true;
    }
  }

  protected Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> modelToTransientConnectionDto() {
    var credentials = modelToCredentialDto();
    if (model.getConnectionType() == ConnectionType.SONARCLOUD) {
      return Either.forRight(new TransientSonarCloudConnectionDto(model.getOrganization(), credentials,
        model.getSonarCloudRegion() != null ? SonarCloudRegion.valueOf(model.getSonarCloudRegion().name()) : SonarCloudRegion.EU));
    } else {
      return Either.forLeft(new TransientSonarQubeConnectionDto(model.getServerUrl(), credentials));
    }
  }

  protected Either<TokenDto, UsernamePasswordDto> modelToCredentialDto() {
    return Either.forLeft(new TokenDto(model.getUsername()));
  }

  private static String message(Exception e) {
    var message = e.getMessage();
    // Message is null for InvocationTargetException, look at the cause
    if (message != null) {
      return message;
    }
    if (e.getCause() != null && e.getCause().getMessage() != null) {
      return e.getCause().getMessage();
    }
    return "";
  }
}
