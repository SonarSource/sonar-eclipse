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
package org.sonarlint.eclipse.ui.internal.binding.assist;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.sonarlint.eclipse.core.internal.telemetry.LinkTelemetry;
import org.sonarlint.eclipse.ui.internal.SonarLintImages;
import org.sonarlint.eclipse.ui.internal.util.BrowserUtils;

class ConfirmConnectionCreationDialog extends MessageDialog {

  public ConfirmConnectionCreationDialog(Shell parentShell, String serverUrl, boolean automaticSetUp) {
    super(parentShell, "Do you trust this SonarQube server?", SonarLintImages.BALLOON_IMG,
      "The server at \"" + serverUrl + "\" is attempting to set up a connection with SonarLint. Letting SonarLint "
      + "connect to an untrusted server is potentially dangerous."
      + (automaticSetUp ? " The following setup will be done automatically." : ""),
      MessageDialog.WARNING, new String[] {"Connect to this SonarQube server", "I don't trust this server"}, 0);
  }

  @Override
  protected Control createCustomArea(Composite parent) {
    var link = new Link(parent, SWT.WRAP);
    link.setText("If you don't trust this server, we recommend canceling this action and <a>manually setting up Connected Mode</a>.");
    link.addListener(SWT.Selection, e -> BrowserUtils.openExternalBrowserWithTelemetry(LinkTelemetry.CONNECTED_MODE_DOCS, e.display));
    return link;
  }

}
