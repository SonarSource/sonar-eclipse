/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2019 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.navigator.CommonActionProvider;
import org.eclipse.ui.navigator.CommonViewer;
import org.eclipse.ui.navigator.ICommonActionExtensionSite;
import org.eclipse.ui.navigator.ICommonViewerSite;
import org.eclipse.ui.navigator.ICommonViewerWorkbenchSite;
import org.sonarlint.eclipse.core.internal.server.IServer;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.Messages;
import org.sonarlint.eclipse.ui.internal.bind.wizard.ProjectBindingWizard;
import org.sonarlint.eclipse.ui.internal.server.actions.NewServerWizardAction;
import org.sonarlint.eclipse.ui.internal.server.actions.ProjectChangeBindingAction;
import org.sonarlint.eclipse.ui.internal.server.actions.ProjectUnbindAction;
import org.sonarlint.eclipse.ui.internal.server.actions.ServerBindProjectsAction;
import org.sonarlint.eclipse.ui.internal.server.actions.ServerDeleteAction;
import org.sonarlint.eclipse.ui.internal.server.actions.ServerEditAction;
import org.sonarlint.eclipse.ui.internal.server.actions.ServerUpdateAction;

import static java.util.Arrays.asList;

public class ServerActionProvider extends CommonActionProvider {
  public static final String NEW_MENU_ID = "org.sonarlint.eclipse.ui.server.newMenuId";

  private ICommonActionExtensionSite actionSite;
  protected Action deleteServerAction;
  protected Action editAction;
  protected Action updateAction;
  protected Action updateBindingAction;
  protected Action bindProjectsAction;
  protected Action unbindProjectsAction;

  public ServerActionProvider() {
    super();
  }

  @Override
  public void init(ICommonActionExtensionSite aSite) {
    super.init(aSite);
    this.actionSite = aSite;
    ICommonViewerSite site = aSite.getViewSite();
    if (site instanceof ICommonViewerWorkbenchSite) {
      StructuredViewer v = aSite.getStructuredViewer();
      if (v instanceof CommonViewer) {
        CommonViewer cv = (CommonViewer) v;
        ICommonViewerWorkbenchSite wsSite = (ICommonViewerWorkbenchSite) site;
        addListeners(cv);
        makeServerActions(cv, wsSite.getSelectionProvider());
      }
    }
  }

  private static void addListeners(final CommonViewer tableViewer) {
    tableViewer.addOpenListener(event -> {
      IStructuredSelection sel = (IStructuredSelection) event.getSelection();
      Object data = sel.getFirstElement();
      if (data instanceof IServer) {
        IServer server = (IServer) data;
        ServerEditAction.openEditWizard(tableViewer.getTree().getShell(), server);
      } else if (data instanceof ISonarLintProject) {
        final WizardDialog dialog = ProjectBindingWizard.createDialog(tableViewer.getTree().getShell(), asList((ISonarLintProject) data));
        dialog.open();
      }
    });
  }

  private void makeServerActions(CommonViewer tableViewer, ISelectionProvider provider) {
    Shell shell = tableViewer.getTree().getShell();
    deleteServerAction = new ServerDeleteAction(shell, provider);
    editAction = new ServerEditAction(shell, provider);
    updateAction = new ServerUpdateAction(provider);
    updateBindingAction = new ProjectChangeBindingAction(shell, provider);
    bindProjectsAction = new ServerBindProjectsAction(shell, provider);
    unbindProjectsAction = new ProjectUnbindAction(shell, provider);
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    actionBars.updateActionBars();
    actionBars.setGlobalActionHandler(ActionFactory.DELETE.getId(), deleteServerAction);
    actionBars.setGlobalActionHandler(ActionFactory.RENAME.getId(), editAction);
    actionBars.setGlobalActionHandler(ActionFactory.REFRESH.getId(), updateAction);
  }

  @Override
  public void fillContextMenu(IMenuManager menu) {
    // This is a temp workaround to clean up the default group that are provided by CNF
    menu.removeAll();

    ICommonViewerSite site = actionSite.getViewSite();
    IStructuredSelection selection = null;
    if (site instanceof ICommonViewerWorkbenchSite) {
      ICommonViewerWorkbenchSite wsSite = (ICommonViewerWorkbenchSite) site;
      selection = (IStructuredSelection) wsSite.getSelectionProvider().getSelection();
    }

    List<IServer> servers = new ArrayList<>();
    List<ISonarLintProject> projects = new ArrayList<>();
    populateServersAndProjects(selection, servers, projects);

    if (!servers.isEmpty() && projects.isEmpty()) {
      menu.add(updateAction);
      if (servers.size() == 1) {
        menu.add(bindProjectsAction);
      }
      menu.add(new Separator());
      if (servers.size() == 1) {
        menu.add(editAction);
      }
      menu.add(deleteServerAction);
      menu.add(new Separator());
    }

    if (projects.isEmpty()) {
      IAction newServerAction = new NewServerWizardAction();
      newServerAction.setText(Messages.actionNewServer);
      menu.add(newServerAction);
    } else if (servers.isEmpty()) {
      menu.add(updateBindingAction);
      menu.add(unbindProjectsAction);
    }

  }

  private static void populateServersAndProjects(IStructuredSelection selection, List<IServer> servers, List<ISonarLintProject> projects) {
    if (selection != null && !selection.isEmpty()) {
      Iterator iterator = selection.iterator();
      while (iterator.hasNext()) {
        Object obj = iterator.next();
        if (obj instanceof IServer) {
          servers.add((IServer) obj);
        } else if (obj instanceof ISonarLintProject) {
          projects.add((ISonarLintProject) obj);
        }
      }
    }
  }

}
