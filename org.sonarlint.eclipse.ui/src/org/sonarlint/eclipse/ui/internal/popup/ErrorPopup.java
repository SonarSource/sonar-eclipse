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
package org.sonarlint.eclipse.ui.internal.popup;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.sonarlint.eclipse.core.documentation.SonarLintDocumentation;
import org.sonarlint.eclipse.ui.internal.SonarLintImages;
import org.sonarlint.eclipse.ui.internal.SonarLintUiPlugin;
import org.sonarlint.eclipse.ui.internal.util.BrowserUtils;
import org.sonarlint.eclipse.ui.internal.util.PopupUtils;

/**
 *  The notification is shown for every error that is supposed to be logged to the SonarQube Console. This will help us
 *  by raising awareness among users to provide us all the necessary information when an error occurs.
 */
public class ErrorPopup extends AbstractSonarLintPopup {
  protected static Set<Integer> ignoredErrorMessageHashes = Collections.synchronizedSet(new HashSet<>());

  private final int errorMessageHash;

  public ErrorPopup(int errorMessageHash) {
    this.errorMessageHash = errorMessageHash;
  }

  @Override
  protected String getMessage() {
    return "Please reach out to us via the Community Forum and provide logs so we can improve the plug-in even "
      + "further. You can also decide to not show this particular error again until the next restart of the IDE.";
  }

  @Override
  protected void createContentArea(Composite composite) {
    super.createContentArea(composite);

    addLink("Open Console",
      e -> SonarLintUiPlugin.getDefault().getSonarConsole().bringConsoleToFront());

    addLink("Community Forum",
      e -> BrowserUtils.openExternalBrowser(SonarLintDocumentation.COMMUNITY_FORUM, getShell().getDisplay()));

    addLink("Don't show again", e -> {
      ignoredErrorMessageHashes.add(errorMessageHash);
      close();
    });

    composite.getShell().addDisposeListener(e -> PopupUtils.removeCurrentlyDisplayedPopup(getClass()));
  }

  @Override
  protected String getPopupShellTitle() {
    return "SonarQube for Eclipse - An error occurred";
  }

  @Override
  protected Image getPopupShellImage(int maximumHeight) {
    return SonarLintImages.IMG_ERROR;
  }

  public static void displayPopupIfNotIgnored(String errorMessage) {
    int errorMessageHash = errorMessage.hashCode();
    if (ErrorPopup.ignoredErrorMessageHashes.contains(errorMessageHash)
      || PopupUtils.popupCurrentlyDisplayed(ErrorPopup.class)) {
      return;
    }

    Display.getDefault().asyncExec(() -> {
      PopupUtils.addCurrentlyDisplayedPopup(ErrorPopup.class);

      var popup = new ErrorPopup(errorMessageHash);
      popup.setFadingEnabled(false);
      popup.setDelayClose(0L);
      popup.open();
    });
  }
}
