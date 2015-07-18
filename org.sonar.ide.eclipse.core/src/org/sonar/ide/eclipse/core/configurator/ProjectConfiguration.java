/*
 * SonarQube Eclipse
 * Copyright (C) 2010-2015 SonarSource
 * sonarqube@googlegroups.com
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
package org.sonar.ide.eclipse.core.configurator;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class ProjectConfiguration {

  private final Set<String> sourceDirs = new LinkedHashSet<String>();
  private final Set<String> testDirs = new LinkedHashSet<String>();
  private final Set<Object> dependentProjects = new HashSet<Object>();

  public Set<Object> dependentProjects() {
    return dependentProjects;
  }

  public Set<String> sourceDirs() {
    return sourceDirs;
  }

  public Set<String> testDirs() {
    return testDirs;
  }

}
