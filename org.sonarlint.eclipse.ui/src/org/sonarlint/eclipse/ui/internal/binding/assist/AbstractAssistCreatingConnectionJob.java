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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.ui.progress.UIJob;
import org.sonarlint.eclipse.core.internal.engine.connected.ConnectionFacade;
import org.sonarlint.eclipse.ui.internal.binding.wizard.connection.ServerConnectionModel;
import org.sonarlint.eclipse.ui.internal.binding.wizard.connection.ServerConnectionModel.ConnectionType;
import org.sonarlint.eclipse.ui.internal.util.DisplayUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

public abstract class AbstractAssistCreatingConnectionJob extends UIJob {
  protected final Either<String, String> serverUrlOrOrganization;
  protected final boolean automaticSetUp;
  protected final boolean fromConnectionSuggestion;
  @Nullable
  protected String connectionId;
  @Nullable
  protected String username;
  @Nullable
  protected final String sonarCloudRegion;

  /** Assistance either to SonarQube / SonarCloud, can be coming from Connection Suggestion! */
  protected AbstractAssistCreatingConnectionJob(String title, Either<String, String> serverUrlOrOrganization,
    boolean automaticSetup, boolean fromConnectionSuggestion, @Nullable String sonarCloudRegion) {
    super(title);
    // We don't want to have this job visible to the user, as there should be a dialog anyway
    setSystem(true);

    this.serverUrlOrOrganization = serverUrlOrOrganization;
    this.automaticSetUp = automaticSetup;
    this.fromConnectionSuggestion = fromConnectionSuggestion;
    this.sonarCloudRegion = sonarCloudRegion;
  }

  @Override
  public IStatus runInUIThread(IProgressMonitor monitor) {
    var shell = DisplayUtils.bringToFront();

    // In order to not blindly accept incoming connection requests via the "Open in IDE" feature, we will trigger a
    // pop-up for the user to manually allow setting up the connection and trusting it.
    AbstractConfirmConnectionCreationDialog dialog;
    if (serverUrlOrOrganization.isLeft()) {
      dialog = new ConfirmSonarQubeConnectionCreationDialog(shell, serverUrlOrOrganization.getLeft(), automaticSetUp);
    } else {
      dialog = new ConfirmSonarCloudConnectionCreationDialog(shell, serverUrlOrOrganization.getRight(), automaticSetUp);
    }
    if (dialog.open() != 0) {
      return Status.CANCEL_STATUS;
    }

    var model = new ServerConnectionModel(fromConnectionSuggestion);
    if (serverUrlOrOrganization.isLeft()) {
      model.setConnectionType(ConnectionType.ONPREMISE);
      model.setServerUrl(serverUrlOrOrganization.getLeft());
    } else {
      model.setOrganization(serverUrlOrOrganization.getRight());
      model.setSonarCloudRegion(ServerConnectionModel.SonarCloudRegion.valueOf(sonarCloudRegion));
    }

    if (fromConnectionSuggestion) {
      model.setNotificationsEnabled(true);
    }

    var connection = createConnection(model);
    if (connection != null) {
      this.connectionId = connection.getId();
      this.username = model.getUsername();
      return Status.OK_STATUS;
    }

    return Status.CANCEL_STATUS;
  }

  @Nullable
  public final String getConnectionId() {
    return connectionId;
  }

  @Nullable
  protected abstract ConnectionFacade createConnection(ServerConnectionModel model);
}
