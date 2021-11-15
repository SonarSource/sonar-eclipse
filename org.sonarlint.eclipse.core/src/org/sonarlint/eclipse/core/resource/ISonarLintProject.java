/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2021 SonarSource SA
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
package org.sonarlint.eclipse.core.resource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.jgit.events.RepositoryEvent;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.sonarlint.eclipse.core.SonarLintLogger;

import static java.util.Objects.requireNonNull;

/**
 * Represents a project for SonarLint. A project will contain the 
 * project level specific SonarLint configuration (binding, exclusions, ...).
 * Should implement hashCode and equals.
 * @since 3.0
 */
public interface ISonarLintProject extends ISonarLintIssuable {

  SonarLintLogger LOGGER = SonarLintLogger.get();

  @Override
  default ISonarLintProject getProject() {
    return this;
  }

  /**
   * Is the project open. Most actions are disabled on closed projects.
   */
  default boolean isOpen() {
    return getResource().isAccessible();
  }

  /**
   * Does full analysis make sense for such project. Full analysis might be very expensive
   * for some Cobol projects, since files are not locally present in the FS.
   * @since 4.1
   */
  default boolean supportsFullAnalysis() {
    return true;
  }

  /**
   * The scope context used to store SonarLint configuration
   */
  default IScopeContext getScopeContext() {
    return new ProjectScope(
      requireNonNull(getResource().getProject(),
        () -> "Unable to decide where SonarLint preferences should be stored for " + getName()));
  }

  /**
   * Unique name of the project that is displayed in logs and UI
   */
  @Override
  String getName();

  /**
   * Working directory for SonarLint, specific to this project.
   */
  Path getWorkingDir();

  /**
   * Verify existence of a file in the current project using relative path
   */
  boolean exists(String relativeFilePath);

  Optional<ISonarLintFile> find(String relativeFilePath);

  /**
   * Object to be notified when SonarLintProjectDecorator (the wave overlay on projects) should be updated.
   * Default implementation returns the corresponding IProject
   */
  Object getObjectToNotify();

  /**
   * @return all SonarLint files contained in this project.
   */
  Collection<ISonarLintFile> files();

  /**
   * Remove recursively all markers created on this project and on children resources.
   */
  default void deleteAllMarkers(String markerId) {
    if (getResource().isAccessible()) {
      try {
        getResource().deleteMarkers(markerId, true, IResource.DEPTH_INFINITE);
      } catch (CoreException e) {
        SonarLintLogger.get().error("Unable to delete markers on project " + getName(), e);
      }
    }
  }

  default void registerGitListeners() {
    LOGGER.info(">>> Registering Git listeners for " + this.getName());
    var resource = getResource();
    var resourcePath = resource.getLocation();
    var resourceFile = resourcePath.toFile();
    var builder = new FileRepositoryBuilder().findGitDir(resourceFile);
    if (builder.getGitDir() == null) {
      LOGGER.error("!!! Could not find Git dir for " + resourceFile);
    } else {
      try (var repository = builder.build()) {
        var listeners = repository.getListenerList();
        listeners.addIndexChangedListener(event -> logBranchName("IndexChangedListener", event));
        listeners.addRefsChangedListener(event -> logBranchName("RefsChangedListener", event));
        listeners.addWorkingTreeModifiedListener(event -> logBranchName("WorkingTreeModifiedListener", event));
        listeners.addConfigChangedListener(event -> logBranchName("ConfigChangedListener", event));
      } catch (IOException e) {
        LOGGER.error("!!! Could not register Git listeners", e);
      }
    }
  }

  private static void logBranchName(String listenerName, RepositoryEvent<?> event) {
    LOGGER.info(">>> " + listenerName + " called with " + event);
    try {
      LOGGER.info(">>> " + listenerName + ": " + event.getRepository().getBranch());
    } catch (IOException e) {
      LOGGER.error("!!! " + listenerName + " could not read project's current branch: ", e);
    }
  }
}
