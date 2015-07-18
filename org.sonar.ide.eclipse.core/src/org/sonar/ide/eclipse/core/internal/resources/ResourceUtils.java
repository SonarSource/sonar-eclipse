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
package org.sonar.ide.eclipse.core.internal.resources;

import java.io.File;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.sonar.ide.eclipse.core.internal.AdapterUtils;
import org.sonar.ide.eclipse.core.internal.SonarCorePlugin;
import org.sonar.ide.eclipse.core.internal.SonarKeyUtils;
import org.sonar.ide.eclipse.core.resources.ISonarResource;

public final class ResourceUtils {

  private static final String PATH_SEPARATOR = "/";

  private ResourceUtils() {
  }

  @CheckForNull
  public static String getSonarResourcePartialKey(IResource resource, String serverVersion) {
    String path = resource.getProjectRelativePath().toString();
    if (StringUtils.isNotBlank(path)) {
      return normalize(path);
    }
    return null;
  }

  @CheckForNull
  private static String normalize(@Nullable String path) {
    if (StringUtils.isBlank(path)) {
      return null;
    }
    String normalizedPath = path;
    normalizedPath = normalizedPath.replace('\\', '/');
    normalizedPath = StringUtils.trim(normalizedPath);
    if (PATH_SEPARATOR.equals(normalizedPath)) {
      return PATH_SEPARATOR;
    }
    normalizedPath = StringUtils.removeStart(normalizedPath, PATH_SEPARATOR);
    normalizedPath = StringUtils.removeEnd(normalizedPath, PATH_SEPARATOR);
    return normalizedPath;
  }

  public static ISonarResource adapt(Object eclipseObject) {
    if (eclipseObject == null) {
      return null;
    }
    return AdapterUtils.adapt(eclipseObject, ISonarResource.class);
  }

  public static IResource findResource(SonarProject sonarProject, String resourceKey) {
    if (sonarProject != null && resourceKey.equals(sonarProject.getKey())) {
      return sonarProject.getProject();
    }
    if (sonarProject != null && resourceKey.startsWith(sonarProject.getKey() + SonarKeyUtils.PROJECT_DELIMITER)) {
      String resourceKeyMinusProjectKey = resourceKey.substring(sonarProject.getKey().length() + 1);
      return sonarProject.getProject().findMember(resourceKeyMinusProjectKey);
    }
    return null;
  }

  @CheckForNull
  public static IPath getAbsolutePath(IPath path) {
    // IPath should be resolved this way in order to handle linked resources (SONARIDE-271)
    IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
    IResource res = root.findMember(path);
    if (res != null) {
      if (res.getLocation() != null) {
        return res.getLocation();
      } else {
        SonarCorePlugin.getDefault().error("Unable to resolve absolute path for " + res.getLocationURI());
        return null;
      }
    } else {
      File external = path.toFile();
      if (external.exists()) {
        return path;
      }
      return null;
    }
  }

}
