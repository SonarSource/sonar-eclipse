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
package org.sonarlint.eclipse.ui.internal.binding.assist;

import org.eclipse.jdt.annotation.Nullable;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.engine.connected.ConnectionFacade;
import org.sonarlint.eclipse.ui.internal.binding.wizard.connection.ServerConnectionModel;

public class AssistCreatingAutomaticConnectionJob extends AbstractAssistCreatingConnectionJob {
  private final String tokenValue;

  public AssistCreatingAutomaticConnectionJob(String serverUrl, String tokenValue) {
    super("Assist automatic creation of Connected Mode", serverUrl, true);
    this.tokenValue = tokenValue;
  }

  @Override
  @Nullable
  protected ConnectionFacade createConnection(ServerConnectionModel model) {
    var connection = SonarLintCorePlugin.getConnectionManager().create(model.getConnectionId(), model.getServerUrl(),
      model.getOrganization(), tokenValue, null, model.getNotificationsDisabled());
    SonarLintCorePlugin.getConnectionManager().addConnection(connection, tokenValue, model.getPassword());
    return connection;
  }
}
