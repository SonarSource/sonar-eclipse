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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.annotation.Nullable;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.backend.SonarLintBackendService;
import org.sonarlint.eclipse.core.internal.engine.AnalysisRequirementNotifications;
import org.sonarlint.eclipse.core.internal.resources.ExclusionItem;
import org.sonarlint.eclipse.core.internal.resources.SonarLintProperty;
import org.sonarlint.eclipse.core.internal.utils.StringUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.DidChangeClientNodeJsPathParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.UpdateStandaloneRulesConfigurationParams;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

public class SonarLintGlobalConfiguration {

  public static final String PREF_MARKER_SEVERITY = "markerSeverity"; //$NON-NLS-1$
  public static final int PREF_MARKER_SEVERITY_DEFAULT = IMarker.SEVERITY_INFO;
  public static final String PREF_ISSUE_DISPLAY_FILTER = "issueFilter"; //$NON-NLS-1$
  public static final String PREF_ISSUE_DISPLAY_FILTER_ALL = "allIssues"; //$NON-NLS-1$
  public static final String PREF_ISSUE_DISPLAY_FILTER_NONRESOLVED = "nonResolved"; //$NON-NLS-1$
  public static final String PREF_ISSUE_PERIOD = "issuePeriod"; //$NON-NLS-1$
  public static final String PREF_ISSUE_PERIOD_ALLTIME = "allTime"; //$NON-NLS-1$
  public static final String PREF_ISSUE_PERIOD_NEWCODE = "newCode"; //$NON-NLS-1$
  public static final String PREF_EXTRA_ARGS = "extraArgs"; //$NON-NLS-1$
  public static final String PREF_FILE_EXCLUSIONS = "fileExclusions"; //$NON-NLS-1$
  public static final String PREF_RULES_CONFIG = "rulesConfig"; //$NON-NLS-1$
  public static final String PREF_DEFAULT = ""; //$NON-NLS-1$
  public static final String PREF_TEST_FILE_GLOB_PATTERNS = "testFileRegexps"; //$NON-NLS-1$
  public static final String PREF_TEST_FILE_GLOB_PATTERNS_DEFAULT = ""; //$NON-NLS-1$
  public static final String PREF_SKIP_CONFIRM_ANALYZE_MULTIPLE_FILES = "skipConfirmAnalyzeMultipleFiles"; //$NON-NLS-1$
  public static final String PREF_NODEJS_PATH = "nodeJsPath"; //$NON-NLS-1$
  public static final String PREF_JAVA17_PATH = "java17Path"; //$NON-NLS-1$
  private static final String PREF_TAINT_VULNERABILITY_DISPLAYED = "taintVulnerabilityDisplayed";
  private static final String PREF_SECRETS_EVER_DETECTED = "secretsEverDetected";
  private static final String PREF_USER_SURVEY_LAST_LINK = "userSurveyLastLink"; //$NON-NLS-1$
  private static final String PREF_SOON_UNSUPPORTED_CONNECTIONS = "soonUnsupportedSonarQubeConnections"; //$NON-NLS-1$
  private static final String PREF_NO_AUTOMATIC_BUILD_WARNING = "noAutomaticBuildWarning"; //$NON-NLS-1$
  private static final String PREF_NO_CONNECTION_SUGGESTIONS = "NoConnectionSuggestions"; //$NON-NLS-1$

  // notifications on missing features from standalone mode / enhanced features from connected mode
  public static final String PREF_IGNORE_MISSING_FEATURES = "ignoreNotificationsAboutMissingFeatures"; //$NON-NLS-1$
  public static final String PREF_IGNORE_ENHANCED_FEATURES = "ignoreNotificationsAboutEnhancedFeatures"; //$NON-NLS-1$

  private SonarLintGlobalConfiguration() {
    // Utility class
  }

  // For which preference is persisted where, see: https://xtranet-sonarsource.atlassian.net/l/cp/wDNK6e74
  private static final IPreferenceChangeListener applicationRootNodeChangeListener = event -> {
    if (PREF_RULES_CONFIG.equals(event.getKey())) {
      SonarLintBackendService.get().getBackend().getRulesService()
        .updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(buildStandaloneRulesConfigDto()));
    }
  };
  private static final IPreferenceChangeListener workspaceRootNodeChangeListener = event -> {
    if (PREF_ISSUE_PERIOD.equals(event.getKey())) {
      SonarLintBackendService.get().getBackend().getNewCodeService().didToggleFocus();
    } else if (PREF_NODEJS_PATH.equals(event.getKey())) {
      AnalysisRequirementNotifications.resetCachedMessages();

      // Call Sloop via RPC
      SonarLintBackendService.get().getBackend().getAnalysisService()
        .didChangeClientNodeJsPath(new DidChangeClientNodeJsPathParams(getNodejsPath()));
    }
  };

  public static void init() {
    var rootNode = getApplicationLevelPreferenceNode();
    rootNode.addPreferenceChangeListener(applicationRootNodeChangeListener);
    rootNode = getWorkspaceLevelPreferenceNode();
    rootNode.addPreferenceChangeListener(workspaceRootNodeChangeListener);
  }

  public static void stop() {
    var rootNode = getApplicationLevelPreferenceNode();
    rootNode.removePreferenceChangeListener(applicationRootNodeChangeListener);
    rootNode = getWorkspaceLevelPreferenceNode();
    rootNode.removePreferenceChangeListener(workspaceRootNodeChangeListener);
  }

  public static String getTestFileGlobPatterns() {
    return Platform.getPreferencesService().getString(SonarLintCorePlugin.UI_PLUGIN_ID, PREF_TEST_FILE_GLOB_PATTERNS, PREF_TEST_FILE_GLOB_PATTERNS_DEFAULT, null);
  }

  // INFO: Not to be confused with Eclipse marker view filters
  public static String getIssueFilter() {
    return Platform.getPreferencesService().getString(SonarLintCorePlugin.UI_PLUGIN_ID, PREF_ISSUE_DISPLAY_FILTER,
      PREF_ISSUE_DISPLAY_FILTER_NONRESOLVED, null);
  }

  public static String getIssuePeriod() {
    return Platform.getPreferencesService().getString(SonarLintCorePlugin.UI_PLUGIN_ID, PREF_ISSUE_PERIOD, PREF_ISSUE_PERIOD_ALLTIME, null);
  }

  public static int getMarkerSeverity() {
    return Platform.getPreferencesService().getInt(SonarLintCorePlugin.UI_PLUGIN_ID, PREF_MARKER_SEVERITY, PREF_MARKER_SEVERITY_DEFAULT, null);
  }

  public static List<SonarLintProperty> getExtraPropertiesForLocalAnalysis(ISonarLintProject project) {
    var props = new ArrayList<SonarLintProperty>();
    // First add all global properties
    var globalExtraArgs = getPreferenceString(PREF_EXTRA_ARGS);
    props.addAll(deserializeExtraProperties(globalExtraArgs));

    // Then add project properties
    var sonarProject = SonarLintCorePlugin.loadConfig(project);
    props.addAll(sonarProject.getExtraProperties());

    return props;
  }

  public static List<SonarLintProperty> deserializeExtraProperties(@Nullable String property) {
    return Stream.of(StringUtils.split(property, "\r\n"))
      .map(keyValuePair -> StringUtils.split(keyValuePair, "="))
      .map(keyValue -> new SonarLintProperty(keyValue[0], keyValue.length > 1 ? keyValue[1] : ""))
      .collect(Collectors.toList());
  }

  public static String serializeFileExclusions(List<ExclusionItem> exclusions) {
    return exclusions.stream()
      .map(ExclusionItem::toStringWithType)
      .collect(Collectors.joining("\r\n"));
  }

  public static String serializeExtraProperties(List<SonarLintProperty> properties) {
    var keyValuePairs = properties.stream()
      .map(p -> p.getName() + "=" + p.getValue())
      .collect(Collectors.toList());
    return StringUtils.joinSkipNull(keyValuePairs, "\r\n");
  }

  public static List<ExclusionItem> deserializeFileExclusions(@Nullable String property) {
    return Stream.of(StringUtils.split(property, "\r\n"))
      .map(ExclusionItem::parse)
      .filter(Objects::nonNull)
      .collect(toList());
  }

  public static List<ExclusionItem> getGlobalExclusions() {
    // add globally-configured exclusions
    var props = getPreferenceString(SonarLintGlobalConfiguration.PREF_FILE_EXCLUSIONS);
    return deserializeFileExclusions(props);
  }

  private static void savePreferences(IEclipsePreferences preferences, Consumer<Preferences> updater, String key, Object value) {
    updater.accept(preferences);
    try {
      preferences.flush();
    } catch (BackingStoreException e) {
      SonarLintLogger.get().error("Could not save preference: " + key + " = " + value, e);
    }
  }

  /** For preferences to be stored at the application level (shared among workspaces and projects) */
  private static IEclipsePreferences getApplicationLevelPreferenceNode() {
    return ConfigurationScope.INSTANCE.getNode(SonarLintCorePlugin.UI_PLUGIN_ID);
  }

  private static IEclipsePreferences getWorkspaceLevelPreferenceNode() {
    return InstanceScope.INSTANCE.getNode(SonarLintCorePlugin.UI_PLUGIN_ID);
  }

  private static String getPreferenceString(String key) {
    return Platform.getPreferencesService().getString(SonarLintCorePlugin.UI_PLUGIN_ID, key, PREF_DEFAULT, null);
  }

  private static void setPreferenceString(IEclipsePreferences preferences, String key, String value) {
    savePreferences(preferences, p -> p.put(key, value), key, value);
  }

  private static boolean getPreferenceBoolean(String key) {
    return Platform.getPreferencesService().getBoolean(SonarLintCorePlugin.UI_PLUGIN_ID, key, false, null);
  }

  private static void setPreferenceBoolean(IEclipsePreferences preferences, String key, boolean value) {
    savePreferences(preferences, p -> p.putBoolean(key, value), key, value);
  }

  public static void disableRule(String ruleKey) {
    var rules = new ArrayList<>(readRulesConfig());
    var ruleToDisable = rules.stream().filter(r -> r.getKey().equals(ruleKey)).findFirst();
    if (ruleToDisable.isPresent()) {
      ruleToDisable.get().setActive(false);
    } else {
      rules.add(new RuleConfig(ruleKey, false));
    }
    saveRulesConfig(rules);
  }

  public static Collection<String> getExcludedRules() {
    return readRulesConfig().stream()
      .filter(r -> !r.isActive())
      .map(RuleConfig::getKey)
      .collect(toList());
  }

  public static Collection<String> getIncludedRules() {
    return readRulesConfig().stream()
      .filter(RuleConfig::isActive)
      .map(RuleConfig::getKey)
      .collect(toList());
  }

  public static Map<String, StandaloneRuleConfigDto> buildStandaloneRulesConfigDto() {
    return readRulesConfig().stream()
      .collect(Collectors.toMap(r -> r.getKey(), r -> new StandaloneRuleConfigDto(r.isActive(), Map.copyOf(r.getParams()))));
  }

  public static Set<RuleConfig> readRulesConfig() {
    var json = getPreferenceString(PREF_RULES_CONFIG);
    return deserializeRulesJson(json);
  }

  private static class RuleConfigGson {

    enum Level {
      @SerializedName("on")
      ON,
      @SerializedName("off")
      OFF
    }

    Level level = Level.ON;
    Map<String, String> parameters;

  }

  private static Set<RuleConfig> deserializeRulesJson(String json) {
    if (StringUtils.isBlank(json)) {
      return Collections.emptySet();
    }
    var mapType = new TypeToken<Map<String, RuleConfigGson>>() {
    }.getType();
    Map<String, RuleConfigGson> rulesByKey = new Gson().fromJson(json, mapType);
    var result = new HashSet<RuleConfig>();
    try {
      rulesByKey.forEach((key, config) -> {
        var rule = new RuleConfig(key, RuleConfigGson.Level.OFF != config.level);
        if (config.parameters != null) {
          config.parameters.forEach((k, v) -> rule.getParams().put(k, v));
        }
        result.add(rule);
      });
    } catch (JsonParseException e) {
      SonarLintLogger.get().error("Invalid JSON format for rules configuration", e);
    }
    return result;
  }

  public static void saveRulesConfig(Collection<RuleConfig> rules) {
    var json = serializeRulesJson(rules);
    setPreferenceString(getApplicationLevelPreferenceNode(), PREF_RULES_CONFIG, json);
  }

  private static String serializeRulesJson(Collection<RuleConfig> rules) {
    Map<String, RuleConfigGson> rulesByKey = new LinkedHashMap<>();
    rules.stream()
      // Sort by key to ensure consistent results
      .sorted(comparing(RuleConfig::getKey))
      .forEach(rule -> {
        var ruleJson = new RuleConfigGson();
        ruleJson.level = rule.isActive() ? RuleConfigGson.Level.ON : RuleConfigGson.Level.OFF;
        if (!rule.getParams().isEmpty()) {
          ruleJson.parameters = new LinkedHashMap<>();
          rule.getParams().entrySet().stream()
            // Sort by key to ensure consistent results
            .sorted(comparing(Entry<String, String>::getKey))
            .forEach(param -> ruleJson.parameters.put(param.getKey(), param.getValue()));
        }
        rulesByKey.put(rule.getKey(), ruleJson);
      });
    var mapType = new TypeToken<Map<String, RuleConfigGson>>() {
    }.getType();
    return new Gson().toJson(rulesByKey, mapType);
  }

  public static void setSkipConfirmAnalyzeMultipleFiles() {
    setPreferenceBoolean(getApplicationLevelPreferenceNode(), PREF_SKIP_CONFIRM_ANALYZE_MULTIPLE_FILES, true);
  }

  public static boolean skipConfirmAnalyzeMultipleFiles() {
    return getPreferenceBoolean(PREF_SKIP_CONFIRM_ANALYZE_MULTIPLE_FILES);
  }

  @Nullable
  public static Path getNodejsPath() {
    return getPathFromPreference(PREF_NODEJS_PATH, "Invalid Node.js path");
  }

  @Nullable
  public static Path getJava17Path() {
    return getPathFromPreference(PREF_JAVA17_PATH, "Invalid Java 17+ path");
  }

  @Nullable
  private static Path getPathFromPreference(String preference, String errorMessage) {
    var pathSetting = StringUtils.trimToNull(getPreferenceString(preference));
    try {
      return pathSetting != null ? Paths.get(pathSetting) : null;
    } catch (InvalidPathException e) {
      SonarLintLogger.get().error(errorMessage, e);
      return null;
    }
  }

  public static boolean taintVulnerabilityNeverBeenDisplayed() {
    return !getPreferenceBoolean(PREF_TAINT_VULNERABILITY_DISPLAYED);
  }

  public static void setTaintVulnerabilityDisplayed() {
    setPreferenceBoolean(getWorkspaceLevelPreferenceNode(), PREF_TAINT_VULNERABILITY_DISPLAYED, true);
  }

  public static boolean secretsNeverDetected() {
    return !getPreferenceBoolean(PREF_SECRETS_EVER_DETECTED);
  }

  public static void setSecretsWereDetected() {
    setPreferenceBoolean(getWorkspaceLevelPreferenceNode(), PREF_SECRETS_EVER_DETECTED, true);
  }

  /** See {@link org.sonarlint.eclipse.ui.internal.popup.SurveyPopup} for more information */
  public static String getUserSurveyLastLink() {
    return getPreferenceString(PREF_USER_SURVEY_LAST_LINK);
  }

  /** See {@link org.sonarlint.eclipse.ui.internal.popup.SurveyPopup} for more information */
  public static void setUserSurveyLastLink(String link) {
    setPreferenceString(getApplicationLevelPreferenceNode(), PREF_USER_SURVEY_LAST_LINK, link);
  }

  /** See {@link org.sonarlint.eclipse.ui.internal.popup.SoonUnsupportedPopup} for more information */
  public static boolean alreadySoonUnsupportedConnection(String connectionVersionCombination) {
    var currentPreference = getPreferenceString(PREF_SOON_UNSUPPORTED_CONNECTIONS);
    if (PREF_DEFAULT.equals(currentPreference)) {
      return false;
    }

    return Set.of(currentPreference.split(",")).contains(connectionVersionCombination);
  }

  /** See {@link org.sonarlint.eclipse.ui.internal.popup.SoonUnsupportedPopup} for more information */
  public static void addSoonUnsupportedConnection(String connectionVersionCombination) {
    var currentPreference = getPreferenceString(PREF_SOON_UNSUPPORTED_CONNECTIONS);
    if (PREF_DEFAULT.equals(currentPreference)) {
      setPreferenceString(getWorkspaceLevelPreferenceNode(), PREF_SOON_UNSUPPORTED_CONNECTIONS, connectionVersionCombination);
      return;
    }

    var currentConnections = new HashSet<>(Arrays.asList(currentPreference.split(",")));
    currentConnections.add(connectionVersionCombination);

    setPreferenceString(getWorkspaceLevelPreferenceNode(), PREF_SOON_UNSUPPORTED_CONNECTIONS, String.join(",", currentConnections));
  }

  public static boolean ignoreMissingFeatureNotifications() {
    // For integration tests we need to disable the notifications
    var property = System.getProperty("sonarlint.internal.ignoreMissingFeature");
    return property == null || property.isBlank()
      ? getPreferenceBoolean(PREF_IGNORE_MISSING_FEATURES)
      : Boolean.parseBoolean(property);
  }

  public static void setIgnoreMissingFeatureNotifications() {
    setPreferenceBoolean(getWorkspaceLevelPreferenceNode(), PREF_IGNORE_MISSING_FEATURES, true);
  }

  public static boolean ignoreEnhancedFeatureNotifications() {
    // For integration tests we need to disable the notifications
    var property = System.getProperty("sonarlint.internal.ignoreEnhancedFeature");
    return property == null || property.isBlank()
      ? getPreferenceBoolean(PREF_IGNORE_ENHANCED_FEATURES)
      : Boolean.parseBoolean(property);
  }

  public static void setIgnoreEnhancedFeatureNotifications() {
    setPreferenceBoolean(getWorkspaceLevelPreferenceNode(), PREF_IGNORE_ENHANCED_FEATURES, true);
  }

  public static boolean noAutomaticBuildWarning() {
    // For integration tests we need to disable the notifications
    var property = System.getProperty("sonarlint.internal.ignoreNoAutomaticBuildWarning");
    return property == null || property.isBlank()
      ? getPreferenceBoolean(PREF_NO_AUTOMATIC_BUILD_WARNING)
      : Boolean.parseBoolean(property);
  }

  public static void setNoAutomaticBuildWarning() {
    setPreferenceBoolean(getWorkspaceLevelPreferenceNode(), PREF_NO_AUTOMATIC_BUILD_WARNING, true);
  }

  public static boolean noConnectionSuggestions() {
    return getPreferenceBoolean(PREF_NO_CONNECTION_SUGGESTIONS);
  }

  public static void setNoConnectionSuggestions() {
    setPreferenceBoolean(getWorkspaceLevelPreferenceNode(), PREF_NO_CONNECTION_SUGGESTIONS, true);
  }
}
