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
package org.sonarlint.eclipse.core.internal.engine.connected;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jdt.annotation.Nullable;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.backend.SonarLintBackendService;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintProjectConfiguration;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintProjectConfiguration.EclipseProjectBinding;
import org.sonarlint.eclipse.core.internal.utils.SonarLintUtils;
import org.sonarlint.eclipse.core.internal.utils.StringUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;

import static java.util.stream.Collectors.toList;

public class ConnectionFacade {

  public static final String OLD_SONARCLOUD_URL = "https://sonarqube.com";

  private final String id;
  private String host;
  @Nullable
  private String organization;
  private boolean hasAuth;
  private final List<IConnectionStateListener> facadeListeners = new ArrayList<>();
  private boolean notificationsDisabled;
  private final Map<String, SonarProjectDto> cachedSonarProjectsByKey = new ConcurrentHashMap<>();
  @Nullable
  private String sonarCloudRegion;

  ConnectionFacade(String id) {
    this.id = id;
  }

  public void notifyAllListenersStateChanged() {
    for (var listener : facadeListeners) {
      listener.stateChanged(this);
    }
  }

  /**
   * Returns the id of this connection.
   * Each connection has a distinct id, fixed for
   * its lifetime.
   *
   * @return the connection id
   */
  public String getId() {
    return id;
  }
  
  @Nullable
  public String getSonarCloudRegion() {
    return sonarCloudRegion;
  }
  
  public ConnectionFacade setSonarCloudRegion(@Nullable String region) {
    this.sonarCloudRegion = region;
    return this;
  }

  /**
   * Returns the host for the connection.
   * The format of the host can be either a qualified or unqualified hostname,
   * or an IP address and must conform to RFC 2732.
   *
   * @return a host string conforming to RFC 2732
   * @see java.net.URL#getHost()
   */
  public String getHost() {
    return host;
  }

  public ConnectionFacade setHost(String host) {
    this.host = host;
    return this;
  }

  @Nullable
  public String getOrganization() {
    return organization;
  }

  public ConnectionFacade setOrganization(@Nullable String organization) {
    this.organization = organization;
    return this;
  }

  public boolean hasAuth() {
    return hasAuth;
  }

  public ConnectionFacade setHasAuth(boolean hasAuth) {
    this.hasAuth = hasAuth;
    return this;
  }

  @Nullable
  public TokenDto getCredentials() {
    if (!hasAuth()) {
      return null;
    }
    @Nullable
    String token;
    try {
      token = ConnectionManager.getToken(this);
    } catch (StorageException e) {
      SonarLintLogger.get().error("Unable to resolve credentials for connection: " + getId());
      return null;
    }
    return new TokenDto(token);
  }

  public synchronized void delete() {
    for (var sonarLintProject : getBoundProjects()) {
      unbind(sonarLintProject);
    }
    SonarLintCorePlugin.getConnectionManager().removeConnection(this);
  }

  public static void unbind(ISonarLintProject project) {
    var config = SonarLintCorePlugin.loadConfig(project);
    config.setProjectBinding(null);
    SonarLintCorePlugin.saveConfig(project, config);
    project.deleteAllMarkers(SonarLintCorePlugin.MARKER_ON_THE_FLY_ID);
    project.deleteAllMarkers(SonarLintCorePlugin.MARKER_ON_THE_FLY_FLOW_ID);
    project.deleteAllMarkers(SonarLintCorePlugin.MARKER_REPORT_ID);
    project.deleteAllMarkers(SonarLintCorePlugin.MARKER_REPORT_FLOW_ID);
  }

  public void updateConfig(String url, @Nullable String organization, String token, boolean notificationsDisabled) {
    this.host = url;
    this.organization = organization;
    this.hasAuth = StringUtils.isNotBlank(token);
    this.notificationsDisabled = notificationsDisabled;
    SonarLintCorePlugin.getConnectionManager().updateConnection(this, token);
  }

  private static Stream<ISonarLintProject> getOpenedProjects() {
    return SonarLintUtils.allProjects().stream()
      .filter(ISonarLintProject::isOpen);
  }

  public Set<String> getBoundProjectKeys() {
    return getOpenedProjects()
      .map(project -> SonarLintCorePlugin.loadConfig(project).getProjectBinding())
      .filter(binding -> binding.filter(b -> id.equals(b.getConnectionId())).isPresent())
      .map(Optional::get)
      .map(EclipseProjectBinding::getProjectKey)
      .collect(Collectors.toSet());
  }

  public List<ISonarLintProject> getBoundProjects() {
    return getOpenedProjects()
      .filter(p -> {
        var config = SonarLintCorePlugin.loadConfig(p);
        return config.getProjectBinding().filter(b -> id.equals(b.getConnectionId())).isPresent();
      }).collect(toList());
  }

  public List<SonarProject> getBoundSonarProjects() {
    return SonarLintUtils.allProjects().stream()
      .filter(ISonarLintProject::isOpen)
      .map(SonarLintCorePlugin::loadConfig)
      .map(SonarLintProjectConfiguration::getProjectBinding)
      .flatMap(Optional::stream)
      .filter(c -> c.getConnectionId().equals(id))
      .map(EclipseProjectBinding::getProjectKey)
      .distinct()
      .sorted()
      .map(projectKey -> {
        var sonarProject = cachedSonarProjectsByKey.get(projectKey);
        if (sonarProject != null) {
          return new SonarProject(id, sonarProject.getKey(), sonarProject.getName());
        } else {
          return new SonarProject(id, projectKey, "<unknown>");
        }
      })
      .collect(toList());
  }

  public Optional<SonarProjectDto> getCachedSonarProject(String projectKey) {
    return Optional.ofNullable(cachedSonarProjectsByKey.get(projectKey));
  }

  public List<ISonarLintProject> getBoundProjects(String projectKey) {
    return SonarLintUtils.allProjects().stream()
      .filter(ISonarLintProject::isOpen)
      .filter(p -> {
        var config = SonarLintCorePlugin.loadConfig(p);
        return config.getProjectBinding().filter(b -> id.equals(b.getConnectionId()) && projectKey.equals(b.getProjectKey())).isPresent();
      }).collect(toList());
  }

  /**
   * Adds the given state listener to this engine.
   * Once registered, a listener starts receiving notification of
   * state changes to this engine. The listener continues to receive
   * notifications until it is removed.
   * Has no effect if an identical listener is already registered.
   *
   * @param listener the engine listener
   * @see #removeConnectionListener(IConnectionStateListener)
   */
  public void addConnectionListener(IConnectionStateListener listener) {
    facadeListeners.add(listener);
  }

  /**
   * Removes the given state listener from this engine. Has no
   * effect if the listener is not registered.
   *
   * @param listener the listener
   * @see #addConnectionListener(IConnectionStateListener)
   */
  public void removeConnectionListener(IConnectionStateListener listener) {
    facadeListeners.remove(listener);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ConnectionFacade)) {
      return false;
    }
    return ((ConnectionFacade) obj).getId().equals(this.getId());
  }

  @Override
  public int hashCode() {
    return getId().hashCode();
  }

  public boolean isSonarCloud() {
    return SonarLintUtils.getSonarCloudUrl(getSonarCloudRegion()).equals(this.host);
  }

  public boolean areNotificationsDisabled() {
    return notificationsDisabled;
  }

  public ConnectionFacade setNotificationsDisabled(boolean value) {
    this.notificationsDisabled = value;
    return this;
  }

  public Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto> toTransientDto() {
    if (isSonarCloud()) {
      return Either.forRight(new TransientSonarCloudConnectionDto(getOrganization(), Either.forLeft(getCredentials()),
        getSonarCloudRegion() != null ? SonarCloudRegion.valueOf(getSonarCloudRegion()) : SonarCloudRegion.EU));
    } else {
      return Either.forLeft(new TransientSonarQubeConnectionDto(getHost(), Either.forLeft(getCredentials())));
    }
  }

  public CompletableFuture<List<SonarProjectDto>> getAndCacheAllSonarProjects() {
    return SonarLintBackendService.get().getAllProjects(this).thenApply(projects -> {
      projects.forEach(p -> this.cachedSonarProjectsByKey.put(p.getKey(), p));
      notifyAllListenersStateChanged();
      return projects;
    });
  }

}
