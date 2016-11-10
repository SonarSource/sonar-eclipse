/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2016 SonarSource SA
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
package org.sonarlint.eclipse.core.internal.jobs;

import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import javax.annotation.CheckForNull;
import org.eclipse.core.resources.ResourcesPlugin;
import org.osgi.framework.FrameworkUtil;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

public class StandaloneSonarLintClientFacade {

  private StandaloneSonarLintEngine client;

  @CheckForNull
  private synchronized StandaloneSonarLintEngine getClient() {
    if (client == null) {
      SonarLintCorePlugin.getDefault().info("Starting standalone SonarLint engine " + FrameworkUtil.getBundle(this.getClass()).getVersion().toString());
      Enumeration<URL> pluginEntries = SonarLintCorePlugin.getDefault().getBundle().findEntries("/plugins", "*.jar", false);
      StandaloneGlobalConfiguration globalConfig = StandaloneGlobalConfiguration.builder()
        .addPlugins(pluginEntries != null ? Collections.list(pluginEntries).toArray(new URL[0]) : (new URL[0]))
        .setWorkDir(ResourcesPlugin.getWorkspace().getRoot().getLocation().append(".sonarlint").append("default").toFile().toPath())
        .setLogOutput(new SonarLintLogOutput())
        .build();
      try {
        client = new StandaloneSonarLintEngineImpl(globalConfig);
      } catch (Throwable e) {
        SonarLintCorePlugin.getDefault().error("Unable to start standalone SonarLint engine", e);
        client = null;
      }
    }
    return client;
  }

  @CheckForNull
  public AnalysisResults runAnalysis(StandaloneAnalysisConfiguration config, IssueListener issueListener) {
    StandaloneSonarLintEngine engine = getClient();
    if (engine != null) {
      return engine.analyze(config, issueListener);
    }
    return null;
  }

  public String getHtmlRuleDescription(String ruleKey) {
    StandaloneSonarLintEngine engine = getClient();
    if (engine != null) {
      RuleDetails ruleDetails = engine.getRuleDetails(ruleKey);
      if (ruleDetails != null) {
        return ruleDetails.getHtmlDescription();
      }
    }
    return "Not found";
  }

  public synchronized void stop() {
    if (client != null) {
      client.stop();
      client = null;
    }
  }

}
