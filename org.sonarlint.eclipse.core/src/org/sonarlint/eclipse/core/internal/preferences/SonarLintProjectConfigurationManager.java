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
package org.sonarlint.eclipse.core.internal.preferences;

import java.util.Set;
import java.util.function.Consumer;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.osgi.service.prefs.BackingStoreException;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintProjectConfiguration.EclipseProjectBinding;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;

import static java.util.Optional.ofNullable;
import static org.sonarlint.eclipse.core.internal.utils.StringUtils.isBlank;
import static org.sonarlint.eclipse.core.internal.utils.StringUtils.isNotBlank;

public class SonarLintProjectConfigurationManager {

  private static final String P_EXTRA_PROPS = "extraProperties";
  private static final String P_FILE_EXCLUSIONS = "fileExclusions";
  // Changing the node name would be a breaking change so we keep the old name "serverId" for now
  public static final String P_CONNECTION_ID = "serverId";
  public static final String P_PROJECT_KEY = "projectKey";
  /**
   * @deprecated since 10.0
   */
  @Deprecated(since = "10.0")
  private static final String P_SQ_PREFIX_KEY = "sqPrefixKey";
  /**
   * @deprecated since 10.0
   */
  @Deprecated(since = "10.0")
  private static final String P_IDE_PREFIX_KEY = "idePrefixKey";
  /**
   * @deprecated since 3.7
   */
  @Deprecated(since = "3.7")
  private static final String P_MODULE_KEY = "moduleKey";
  private static final String P_AUTO_ENABLED_KEY = "autoEnabled";
  public static final String P_BINDING_SUGGESTIONS_DISABLED_KEY = "bindingSuggestionsDisabled";

  private static final Set<String> BINDING_RELATED_PROPERTIES = Set.of(P_PROJECT_KEY, P_CONNECTION_ID, P_BINDING_SUGGESTIONS_DISABLED_KEY);

  public static void registerPreferenceChangeListenerForBindingProperties(ISonarLintProject project, Consumer<ISonarLintProject> listener) {
    ofNullable(project.getScopeContext().getNode(SonarLintCorePlugin.PLUGIN_ID))
      .ifPresent(node -> {
        node.addPreferenceChangeListener(event -> {
          if (BINDING_RELATED_PROPERTIES.contains(event.getKey())) {
            listener.accept(project);
          }
        });
      });
  }

  public SonarLintProjectConfiguration load(IScopeContext projectScope, String projectName) {
    var projectNode = projectScope.getNode(SonarLintCorePlugin.PLUGIN_ID);
    var projectConfig = new SonarLintProjectConfiguration();
    if (projectNode == null) {
      return projectConfig;
    }

    var extraArgsAsString = projectNode.get(P_EXTRA_PROPS, null);
    var sonarProperties = SonarLintGlobalConfiguration.deserializeExtraProperties(extraArgsAsString);
    var fileExclusionsAsString = projectNode.get(P_FILE_EXCLUSIONS, null);
    var fileExclusions = SonarLintGlobalConfiguration.deserializeFileExclusions(fileExclusionsAsString);

    projectConfig.getExtraProperties().addAll(sonarProperties);
    projectConfig.getFileExclusions().addAll(fileExclusions);
    var projectKey = projectNode.get(P_PROJECT_KEY, "");
    var moduleKey = projectNode.get(P_MODULE_KEY, "");
    if (isBlank(projectKey) && isNotBlank(moduleKey)) {
      SonarLintLogger.get().info("Binding configuration of project '" + projectName + "' is outdated. Please rebind this project.");
    }
    projectNode.remove(P_MODULE_KEY);
    var connectionId = projectNode.get(P_CONNECTION_ID, "");
    if (isNotBlank(connectionId) && isNotBlank(projectKey)) {
      projectConfig.setProjectBinding(new EclipseProjectBinding(connectionId, projectKey));
    }
    projectConfig.setAutoEnabled(projectNode.getBoolean(P_AUTO_ENABLED_KEY, true));
    projectConfig.setBindingSuggestionsDisabled(projectNode.getBoolean(P_BINDING_SUGGESTIONS_DISABLED_KEY, false));
    return projectConfig;
  }

  public void save(IScopeContext projectScope, SonarLintProjectConfiguration configuration) {
    var projectNode = projectScope.getNode(SonarLintCorePlugin.PLUGIN_ID);
    if (projectNode == null) {
      throw new IllegalStateException("Unable to get SonarLint settings node");
    }

    if (!configuration.getExtraProperties().isEmpty()) {
      var props = SonarLintGlobalConfiguration.serializeExtraProperties(configuration.getExtraProperties());
      projectNode.put(P_EXTRA_PROPS, props);
    } else {
      projectNode.remove(P_EXTRA_PROPS);
    }

    if (!configuration.getFileExclusions().isEmpty()) {
      var props = SonarLintGlobalConfiguration.serializeFileExclusions(configuration.getFileExclusions());
      projectNode.put(P_FILE_EXCLUSIONS, props);
    } else {
      projectNode.remove(P_FILE_EXCLUSIONS);
    }

    configuration.getProjectBinding().ifPresentOrElse(
      binding -> {
        projectNode.put(P_PROJECT_KEY, binding.getProjectKey());
        projectNode.put(P_CONNECTION_ID, binding.getConnectionId());
      },
      () -> {
        projectNode.remove(P_PROJECT_KEY);
        projectNode.remove(P_CONNECTION_ID);
        projectNode.remove(P_SQ_PREFIX_KEY);
        projectNode.remove(P_IDE_PREFIX_KEY);
      });

    projectNode.putBoolean(P_AUTO_ENABLED_KEY, configuration.isAutoEnabled());
    projectNode.putBoolean(P_BINDING_SUGGESTIONS_DISABLED_KEY, configuration.isBindingSuggestionsDisabled());
    try {
      projectNode.flush();
    } catch (BackingStoreException e) {
      SonarLintLogger.get().error("Failed to save project configuration", e);
    }
  }

}
