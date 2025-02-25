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

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.ui.PlatformUI;
import org.sonarlint.eclipse.core.internal.engine.connected.ConnectionFacade;
import org.sonarlint.eclipse.ui.internal.binding.wizard.connection.AbstractConnectionWizard;
import org.sonarlint.eclipse.ui.internal.binding.wizard.connection.ServerConnectionModel;
import org.sonarlint.eclipse.ui.internal.binding.wizard.connection.ServerConnectionWizard;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

public class AssistCreatingManualConnectionJob extends AbstractAssistCreatingConnectionJob {
  public AssistCreatingManualConnectionJob(Either<String, String> serverUrlOrOrganization, @Nullable String sonarCloudRegion) {
    super("Assist manual creation of Connected Mode", serverUrlOrOrganization, false, false, sonarCloudRegion);
  }

  @Override
  @Nullable
  protected ConnectionFacade createConnection(ServerConnectionModel model) {
    var wizard = new ServerConnectionWizard(model);
    wizard.setSkipBindingWizard(true);
    var dialog = AbstractConnectionWizard.createDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
      wizard);
    dialog.setBlockOnOpen(true);
    dialog.open();
    return wizard.getResultServer();
  }
}
