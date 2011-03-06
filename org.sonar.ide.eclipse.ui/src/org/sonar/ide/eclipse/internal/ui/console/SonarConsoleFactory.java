/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2010-2011 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.ide.eclipse.internal.ui.console;

import org.eclipse.ui.console.IConsoleFactory;
import org.sonar.ide.eclipse.ui.ISonarConsole;
import org.sonar.ide.eclipse.ui.SonarUiPlugin;

/**
 * Used to show the console from the "Open Console" drop-down action in Console view.
 * 
 * @see org.eclipse.ui.console.consoleFactory extension point
 */
public class SonarConsoleFactory implements IConsoleFactory {

  public void openConsole() {
    ISonarConsole console = SonarUiPlugin.getDefault().getSonarConsole();
    ((SonarConsole) console).showConsole();
  }

}
