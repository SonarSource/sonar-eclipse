/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2020 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.adapter.Adapters;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.job.SubscribeToNotificationsJob;

public class SonarLintProjectEventListener implements IResourceChangeListener {

  @Override
  public void resourceChanged(IResourceChangeEvent event) {
    if (event.getType() == IResourceChangeEvent.POST_CHANGE) {
      List<ISonarLintProject> projectToSubscribeToNotifications = new ArrayList<>();
      List<ISonarLintProject> projectToUnsubscribeFromNotifications = new ArrayList<>();
      try {
        event.getDelta().accept(delta -> visitDelta(delta, projectToSubscribeToNotifications, projectToUnsubscribeFromNotifications));
      } catch (CoreException e) {
        SonarLintLogger.get().error(e.getMessage(), e);
      }
      if (!projectToSubscribeToNotifications.isEmpty()) {
        new SubscribeToNotificationsJob(projectToSubscribeToNotifications).schedule();
      }
      projectToUnsubscribeFromNotifications.forEach(p -> SonarLintCorePlugin.getInstance().notificationsManager().unsubscribe(p));
    }
  }

  private static boolean visitDelta(IResourceDelta delta, List<ISonarLintProject> projectToSubscribeToNotifications,
    List<ISonarLintProject> projectToUnsubscribeFromNotifications) {
    if ((delta.getFlags() & IResourceDelta.OPEN) != 0) {
      ISonarLintProject project = Adapters.adapt(delta.getResource(), ISonarLintProject.class);
      if (project != null) {
        if (project.isOpen() && SonarLintCorePlugin.loadConfig(project).isBound()) {
          projectToSubscribeToNotifications.add(project);
        } else {
          projectToUnsubscribeFromNotifications.add(project);
        }
      }
      return false;
    }
    return true;
  }
}
