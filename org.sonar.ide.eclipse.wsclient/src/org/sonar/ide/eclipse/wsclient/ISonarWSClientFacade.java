/*
 * Sonar Eclipse
 * Copyright (C) 2010-2013 SonarSource
 * dev@sonar.codehaus.org
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.ide.eclipse.wsclient;

import org.eclipse.core.runtime.IProgressMonitor;
import org.sonar.ide.eclipse.common.issues.ISonarIssue;
import org.sonar.ide.eclipse.common.issues.IssueSeverity;

import java.util.Date;
import java.util.List;

public interface ISonarWSClientFacade {

  public static enum ConnectionTestResult {
    OK, CONNECT_ERROR, AUTHENTICATION_ERROR;
  }

  ConnectionTestResult testConnection();

  String getServerVersion();

  List<ISonarRemoteModule> listAllRemoteModules();

  List<ISonarRemoteModule> searchRemoteModules(String partialName);

  boolean exists(String resourceKey);

  Date getLastAnalysisDate(String resourceKey);

  String[] getRemoteCode(String resourceKey);

  List<ISonarIssue> getRemoteIssuesRecursively(String resourceKey, IProgressMonitor monitor, IssueSeverity minSeverity);

  List<ISonarIssue> getRemoteIssues(String resourceKey, IProgressMonitor monitor, IssueSeverity minSeverity);

  String[] getChildrenKeys(String resourceKey);

}
