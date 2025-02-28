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
package org.sonarlint.eclipse.core.internal.utils;

import java.net.URI;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.Nullable;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.analysis.SonarLintLanguage;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.backend.SonarLintBackendService;
import org.sonarlint.eclipse.core.internal.engine.connected.ConnectionFacade;
import org.sonarlint.eclipse.core.internal.extension.SonarLintExtensionTracker;
import org.sonarlint.eclipse.core.resource.ISonarLintFile;
import org.sonarlint.eclipse.core.resource.ISonarLintIssuable;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.core.resource.ISonarLintProjectsProvider;
import org.sonarsource.sonarlint.core.rpc.client.ConfigScopeNotFoundException;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class SonarLintUtils {
  /**
   *  This internal property is used between the CDT integration sub-plug-in and the CORE bundle as the communication
   *  is only one-way and happening via the extension point:
   *
   *  When CDT ain't yet ready for a file, it cannot provide values for the `build-wrapper-dump.json` that is crucial
   *  for the CFamily analysis. This can happen in corner cases like creating a project from within the IDE with a
   *  source code file already present -> then the CDT integration for that project is not yet ready.
   */
  public static final String SONARLINT_ANALYSIS_CDT_EXCLUSION_PROPERY = "sonarlint.internal.analysis.cdt.exclusion";

  /**
   *  Enabled languages should be consistent with https://www.sonarsource.com/products/sonarlint/features/eclipse!
   *
   *  Currently the only sub-plugins bringing their own languages are JDT (Java/JSP) and CDT (C/C++).
   */
  private static final Set<SonarLintLanguage> DEFAULT_LANGUAGES = EnumSet.of(SonarLintLanguage.PYTHON, SonarLintLanguage.JS, SonarLintLanguage.TS,
    SonarLintLanguage.HTML, SonarLintLanguage.CSS, SonarLintLanguage.PHP, SonarLintLanguage.XML, SonarLintLanguage.SECRETS);
  private static final Set<SonarLintLanguage> OPTIONAL_LANGUAGES = EnumSet.of(SonarLintLanguage.JAVA,
    SonarLintLanguage.JSP, SonarLintLanguage.C, SonarLintLanguage.CPP);
  private static final Set<SonarLintLanguage> DEFAULT_CONNECTED_LANGUAGES = EnumSet.of(SonarLintLanguage.ABAP,
    SonarLintLanguage.APEX, SonarLintLanguage.COBOL, SonarLintLanguage.JCL, SonarLintLanguage.KOTLIN,
    SonarLintLanguage.PLI, SonarLintLanguage.PLSQL, SonarLintLanguage.RPG, SonarLintLanguage.RUBY,
    SonarLintLanguage.SCALA, SonarLintLanguage.TSQL);

  private SonarLintUtils() {
    // utility class, forbidden constructor
  }

  public static String getSonarCloudUrl() {
    // For testing we need to allow changing default URL
    return System.getProperty("sonarlint.internal.sonarcloud.url", "https://sonarcloud.io");
  }

  public static boolean isSonarLintFileCandidate(IResource resource) {
    if (!resource.exists() || resource.isDerived(IResource.CHECK_ANCESTORS) || resource.isHidden(IResource.CHECK_ANCESTORS)) {
      return false;
    }
    // Ignore .project, .settings, that are not considered hidden on Windows...
    // Also ignore .class (SLE-65)
    if (resource.getName().startsWith(".") || "class".equals(resource.getFileExtension())) {
      return false;
    }
    return true;
  }

  @Nullable
  public static ISonarLintFile findFileFromUri(URI fileUri) {
    var files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(fileUri);
    if (files.length == 0) {
      return null;
    }
    for (var file : files) {
      var slFile = SonarLintUtils.adapt(file, ISonarLintFile.class,
        "[SonarLintUtils#findFileFromUri] Try find file from '" + file.getName() + "'");
      if (slFile != null) {
        return slFile;
      }
    }
    return null;
  }

  public static String getPluginVersion() {
    return SonarLintCorePlugin.getInstance().getBundle().getVersion().toString();
  }

  public static Set<SonarLintLanguage> getStandaloneEnabledLanguages() {
    var enabledLanguages = EnumSet.noneOf(SonarLintLanguage.class);
    enabledLanguages.addAll(DEFAULT_LANGUAGES);

    var configurators = SonarLintExtensionTracker.getInstance().getAnalysisConfigurators();
    for (var configurator : configurators) {
      var enableLanguages = configurator.enableLanguages();
      enableLanguages.stream().filter(OPTIONAL_LANGUAGES::contains).forEach(enabledLanguages::add);
    }
    return enabledLanguages;
  }

  public static Set<SonarLintLanguage> getConnectedEnabledLanguages() {
    var enabledLanguages = EnumSet.noneOf(SonarLintLanguage.class);
    enabledLanguages.addAll(DEFAULT_CONNECTED_LANGUAGES);
    return enabledLanguages;
  }

  @Nullable
  public static SonarLintLanguage convert(Language rpcLanguage) {
    try {
      return SonarLintLanguage.valueOf(rpcLanguage.name());
    } catch (IllegalArgumentException e) {
      // The language doesn't exist in SLE
      return null;
    }
  }

  public static int getPlatformPid() {
    return (int) ProcessHandle.current().pid();
  }

  public static ThreadFactory threadFactory(String name, boolean daemon) {
    return runnable -> {
      var result = new Thread(runnable, name);
      result.setDaemon(daemon);
      return result;
    };
  }

  /** Check whether a file is bound to SQ / SC via its project */
  public static boolean isBoundToConnection(ISonarLintIssuable f) {
    var config = SonarLintCorePlugin.loadConfig(f.getProject());
    return config.isBound()
      && config.getProjectBinding().isPresent();
  }

  /** Check whether a file is bound to SQ / SC via its project */
  public static boolean isBoundToConnection(ISonarLintIssuable f, ConnectionFacade facade) {
    var config = SonarLintCorePlugin.loadConfig(f.getProject());
    return config.isBound()
      && config.getProjectBinding().isPresent()
      && facade.getId().equals(config.getProjectBinding().get().getConnectionId());
  }

  /**
   *  Check if a project has a connection to a SonarQube 10.2+ instance can therefore offer the user the option to
   *  transition anticipated issues. If the project is not bound to any connection, just log it and provide an error
   *  if checking the server failed for any reason.
   *
   *  INFO: Because it is costly, maybe cache the information in the future and only check periodically!
   */
  public static boolean checkProjectSupportsAnticipatedStatusChange(ISonarLintProject project) {
    var config = SonarLintCorePlugin.loadConfig(project);
    if (!config.isBound()) {
      return false;
    }
    var viableForStatusChange = false;
    try {
      viableForStatusChange = SonarLintBackendService.get().checkAnticipatedStatusChangeSupported(project).join().isSupported();
    } catch (Exception err) {
      SonarLintLogger.get().error("Could not check if project is bound and if connection is supporting anticipated issues", err);
    }

    return viableForStatusChange;
  }

  /**
   *  Wrapper around {@link org.eclipse.core.runtime.Adapters#adapt(Object, Class)} in order to log debug information
   *  which we then can use when debugging / investigating issues. Tracing is used for checking when this does not
   *  succeed!
   */
  @Nullable
  public static <T> T adapt(@Nullable Object sourceObject, Class<T> adapter, String trace) {
    if (sourceObject == null) {
      SonarLintLogger.get().traceIdeMessage(trace);
      return null;
    }

    var adapted = Adapters.adapt(sourceObject, adapter);
    if (adapted == null) {
      SonarLintLogger.get().traceIdeMessage(trace + " -> '" + sourceObject.toString() + "' could not be adapted to '"
        + adapter.getCanonicalName() + "'");
    }

    return adapted;
  }

  public static ISonarLintProject resolveProject(String configScopeId) throws ConfigScopeNotFoundException {
    var projectOpt = tryResolveProject(configScopeId);
    if (projectOpt.isEmpty()) {
      SonarLintLogger.get().debug("Unable to resolve project: " + configScopeId);
      throw new ConfigScopeNotFoundException();
    }
    return projectOpt.get();
  }

  public static Optional<ISonarLintProject> tryResolveProject(String configScopeId) {
    var projectUri = URI.create(configScopeId);
    return Stream.of(ResourcesPlugin.getWorkspace().getRoot().findContainersForLocationURI(projectUri))
      .map(c -> adapt(c, ISonarLintProject.class,
        "[SonarLintUtils#tryResolveProject] Try adapt configScopeId '" + configScopeId + "'"))
      .filter(Objects::nonNull)
      .findFirst();
  }

  /**
   *  This is here so it can be accessed by sub-plug-ins making use of this extension point. The contract applies that
   *  sub-plug-ins using the {@link org.sonarlint.eclipse.core.resource.ISonarLintProjectsProvider} cannot use this
   *  method!
   *
   *  @return all projects in the workspace that where adaptable to ISonarLintProject
   */
  public static Collection<ISonarLintProject> allProjects() {
    return SonarLintExtensionTracker.getInstance().getProjectsProviders().stream()
      .map(ISonarLintProjectsProvider::get)
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());
  }

  /**
   *  Checks whether one relative path inside the Eclipse workspace is a child of another path. This doesn't have to be
   *  a child denoted by a separator like in a file system (with slashes for example).
   */
  public static boolean isChild(IPath possibleChild, IPath possibleParent) {
    var possibleChildPath = possibleChild.makeAbsolute().toOSString();
    var possibleParentPath = possibleParent.makeAbsolute().toOSString();
    return possibleChildPath.startsWith(possibleParentPath);
  }

  /**
   *  This was not moved to a sub-plugin implementing the "IProjectScopeProvider" as it would be a bit too costly and
   *  also might have blown up the list of exclusions. It also wouldn't have made sense to rely (optionally) on Eclipse
   *  plug-ins providing VCS support just to read the name of their "specific" directory.
   *
   *  Here we catch Git, Mercurial, and Apache Subversion as they're the most common ones.
   */
  public static boolean insideVCSFolder(IPath path) {
    var osString = path.makeAbsolute().toOSString();
    var satisfiesCheck = osString.contains(".git");
    satisfiesCheck = satisfiesCheck || osString.contains(".hg");
    satisfiesCheck = satisfiesCheck || osString.contains(".svn");
    return satisfiesCheck;
  }

  /**
   *  This was not moved to a sub-plugin implementing the "IProjectScopeProvider" as it would be a bit too costly and
   *  also might have blown up the list of exclusions. It also wouldn't have made sense as there is no direct Eclipse
   *  plug-in to rely on for getting these values.
   */
  public static boolean isNodeJsRelated(IPath path) {
    var osString = path.makeAbsolute().toOSString();
    var satisfiesCheck = osString.contains("node_modules");
    satisfiesCheck = satisfiesCheck || osString.contains("package-lock.json");
    return satisfiesCheck;
  }

  /**
   *  This was not moved to a sub-plugin implementing the "IProjectScopeProvider" as it would be a bit too costly and
   *  also might have blown up the list of exclusions. It also wouldn't have made sense to rely (optionally) on the
   *  Eclipse PyDev plug-in.
   *
   *  Here we catch the most common Python virtual environment names.
   */
  public static boolean isPythonRelated(IPath path) {
    var osString = path.makeAbsolute().toOSString();
    var satisfiesCheck = osString.contains("venv");
    satisfiesCheck = satisfiesCheck || osString.contains("pyenv");
    satisfiesCheck = satisfiesCheck || osString.contains("pyvenv");
    satisfiesCheck = satisfiesCheck || osString.contains("virtualenv");
    return satisfiesCheck;
  }

  /**
   *  On macOS/Linux Eclipse PDE while working with Maven/Tycho creates folders starting with "file:/" in every Eclipse
   *  project that contains a `product` definition. In this folder unpacked parts of plug-ins will be saved and
   *  therefore triggers a lot of events we don't want to react to.
   *
   *  @see <a href="https://sonarsource.atlassian.net/browse/SLE-1098">SLE-1098</a>
   */
  public static boolean isIncorrectEclipsePDE(IPath path) {
    var osString = path.makeAbsolute().toOSString();
    return osString.contains("/file:/");
  }

  // This can also be used in sub-plug-ins!
  public static String getConfigScopeId(IProject project) {
    return project.getLocationURI().toString();
  }
}
