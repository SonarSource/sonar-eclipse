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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.INodeChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.NodeChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jdt.annotation.Nullable;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.backend.SonarLintBackendService;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintProjectConfiguration;
import org.sonarlint.eclipse.core.internal.utils.SonarLintUtils;
import org.sonarlint.eclipse.core.internal.utils.StringUtils;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;

public class ConnectionManager {
  // Changing the preference node name would be a breaking change, so we keep the old name "servers" for now
  public static final String PREF_CONNECTIONS = "servers";
  static final String AUTH_ATTRIBUTE = "auth";
  static final String URL_ATTRIBUTE = "url";
  static final String ORG_ATTRIBUTE = "org";
  static final String REGION_ATTRIBUTE = "region";

  // Even though this now storing only the token, we cannot rename the attribute as updating from
  // previous versions would not work anymore - keep compatibility!
  static final String TOKEN_ATTRIBUTE = "username";

  static final String NOTIFICATIONS_DISABLED_ATTRIBUTE = "notificationsDisabled";

  private static final byte EVENT_CHANGED = 1;
  private static final byte EVENT_REMOVED = 2;

  private final Map<String, ConnectionFacade> facadesByConnectionId = new LinkedHashMap<>();

  private final List<IConnectionManagerListener> connectionsListeners = new ArrayList<>();

  private final IPreferenceChangeListener connectedEngineChangeListener = event -> {
    try {
      if (!event.getNode().nodeExists("") || !event.getNode().parent().nodeExists("")) {
        // Deletion in progress
        return;
      }
    } catch (BackingStoreException e) {
      throw new IllegalStateException(e);
    }
    var connectionId = getConnectionIdFromNodeName(event.getNode().name());
    var old = facadesByConnectionId.get(connectionId);
    if (old != null) {
      loadConnection(event.getNode(), old);
      fireConnectionEvent(old, EVENT_CHANGED);
    } else {
      var newFacade = new ConnectionFacade(connectionId);
      loadConnection(event.getNode(), newFacade);
      facadesByConnectionId.put(newFacade.getId(), newFacade);
      fireConnectionAddedEvent(newFacade);
    }
  };

  private final INodeChangeListener connectionsNodeChangeListener = new INodeChangeListener() {

    @Override
    public void removed(NodeChangeEvent event) {
      var connectionId = getConnectionIdFromNodeName(event.getChild().name());
      var connectionToRemove = facadesByConnectionId.get(connectionId);
      facadesByConnectionId.remove(connectionId);
      if (connectionToRemove != null) {
        fireConnectionEvent(connectionToRemove, EVENT_REMOVED);
      }
    }

    @Override
    public void added(NodeChangeEvent event) {
      ((IEclipsePreferences) event.getChild()).addPreferenceChangeListener(connectedEngineChangeListener);
    }
  };

  private final INodeChangeListener rootNodeChangeListener = new INodeChangeListener() {

    @Override
    public void removed(NodeChangeEvent event) {
      if (event.getChild().name().equals(PREF_CONNECTIONS)) {
        var removedConnections = new ArrayList<>(facadesByConnectionId.values());
        facadesByConnectionId.clear();
        for (var connection : removedConnections) {
          fireConnectionEvent(connection, EVENT_REMOVED);
        }
        // Reload default values
        facadesByConnectionId.putAll(loadServersList(DefaultScope.INSTANCE.getNode(SonarLintCorePlugin.PLUGIN_ID).node(PREF_CONNECTIONS)));
        for (var connection : facadesByConnectionId.values()) {
          fireConnectionAddedEvent(connection);
        }
      }
    }

    @Override
    public void added(NodeChangeEvent event) {
      if (event.getChild().name().equals(PREF_CONNECTIONS)) {
        // Default connections
        var removedConnections = new ArrayList<>(facadesByConnectionId.values());
        facadesByConnectionId.clear();
        for (var connection : removedConnections) {
          fireConnectionEvent(connection, EVENT_REMOVED);
        }
        ((IEclipsePreferences) event.getChild()).addNodeChangeListener(connectionsNodeChangeListener);
      }
    }
  };

  public void init() {
    var rootNode = getSonarLintPreferenceNode();
    rootNode.addNodeChangeListener(rootNodeChangeListener);
    try {
      if (rootNode.nodeExists(PREF_CONNECTIONS)) {
        var connectionsNode = rootNode.node(PREF_CONNECTIONS);
        ((IEclipsePreferences) connectionsNode).addNodeChangeListener(connectionsNodeChangeListener);
        facadesByConnectionId.putAll(loadServersList(connectionsNode));
        for (var connectionNodeName : connectionsNode.childrenNames()) {
          var connectionNode = ((IEclipsePreferences) connectionsNode.node(connectionNodeName));
          connectionNode.addPreferenceChangeListener(connectedEngineChangeListener);
        }
      } else {
        facadesByConnectionId.putAll(loadServersList(DefaultScope.INSTANCE.getNode(SonarLintCorePlugin.PLUGIN_ID).node(PREF_CONNECTIONS)));
      }
    } catch (BackingStoreException e) {
      throw unableToLoadConnectionList(e);
    }
  }

  public void stop() {
    var rootNode = getSonarLintPreferenceNode();
    rootNode.removeNodeChangeListener(rootNodeChangeListener);
    try {
      if (rootNode.nodeExists(PREF_CONNECTIONS)) {
        var connectionsNode = rootNode.node(PREF_CONNECTIONS);
        ((IEclipsePreferences) connectionsNode).removeNodeChangeListener(connectionsNodeChangeListener);
        for (var connectionNodeName : connectionsNode.childrenNames()) {
          var connectionNode = ((IEclipsePreferences) connectionsNode.node(connectionNodeName));
          connectionNode.removePreferenceChangeListener(connectedEngineChangeListener);
        }
      }
    } catch (BackingStoreException e) {
      throw unableToLoadConnectionList(e);
    }
    connectionsListeners.clear();
  }

  public void addConnectionManagerListener(IConnectionManagerListener listener) {
    synchronized (connectionsListeners) {
      connectionsListeners.add(listener);
    }
  }

  public void removeConnectionManagerListener(IConnectionManagerListener listener) {
    synchronized (connectionsListeners) {
      connectionsListeners.remove(listener);
    }
  }

  private void fireConnectionEvent(final ConnectionFacade connectionFacade, byte b) {
    for (IConnectionManagerListener srl : getListeners()) {
      if (b == EVENT_CHANGED) {
        srl.connectionChanged(connectionFacade);
      } else {
        srl.connectionRemoved(connectionFacade);
      }
    }
  }

  private void fireConnectionAddedEvent(ConnectionFacade connection) {
    for (IConnectionManagerListener srl : getListeners()) {
      srl.connectionAdded(connection);
    }
  }

  private Iterable<IConnectionManagerListener> getListeners() {
    var clone = new ArrayList<IConnectionManagerListener>();
    clone.addAll(connectionsListeners);
    return clone;
  }

  private static IEclipsePreferences getSonarLintPreferenceNode() {
    return InstanceScope.INSTANCE.getNode(SonarLintCorePlugin.PLUGIN_ID);
  }

  private static Map<String, ConnectionFacade> loadServersList(Preferences serversNode) {
    Map<String, ConnectionFacade> result = new LinkedHashMap<>();
    try {
      for (var serverNodeName : serversNode.childrenNames()) {
        var serverNode = serversNode.node(serverNodeName);
        var serverId = getConnectionIdFromNodeName(serverNodeName);
        var facade = new ConnectionFacade(serverId);
        loadConnection(serverNode, facade);
        result.put(facade.getId(), facade);
      }
    } catch (BackingStoreException e) {
      throw unableToLoadConnectionList(e);
    }
    return result;
  }

  private static IllegalStateException unableToLoadConnectionList(BackingStoreException e) {
    return new IllegalStateException("Unable to load connections list", e);
  }

  private static void loadConnection(Preferences connectionNode, ConnectionFacade facade) {
    var url = connectionNode.get(URL_ATTRIBUTE, "");
    url = StringUtils.removeEnd(url, "/");
    if (ConnectionFacade.OLD_SONARCLOUD_URL.equals(url)) {
      // Migration
      url = SonarLintUtils.getSonarCloudUrl();
      connectionNode.put(URL_ATTRIBUTE, url);
    }
    update(facade,
      url,
      connectionNode.get(ORG_ATTRIBUTE, null),
      connectionNode.get(REGION_ATTRIBUTE, null),
      connectionNode.getBoolean(AUTH_ATTRIBUTE, false),
      connectionNode.getBoolean(NOTIFICATIONS_DISABLED_ATTRIBUTE, false));
  }

  private static String getConnectionIdFromNodeName(String name) {
    return StringUtils.urlDecode(name);
  }

  public void addConnection(ConnectionFacade facade, String token) {
    if (facadesByConnectionId.containsKey(facade.getId())) {
      throw new IllegalStateException("There is already a connection with id '" + facade.getId() + "'");
    }
    if (StringUtils.isNotBlank(token)) {
      storeCredentials(facade, token);
    }
    addOrUpdateProperties(facade);
    facadesByConnectionId.put(facade.getId(), facade);
    fireConnectionAddedEvent(facade);
  }

  /**
   * @return true if the new credentials are different compared to what is in the secure storage
   */
  private static boolean storeCredentials(ConnectionFacade connectionFacade, String token) {
    try {
      var secureConnectionsNode = getSecureConnectionsNode();
      var secureConnectionNode = secureConnectionsNode.node(getConnectionNodeName(connectionFacade.getId()));
      var previousToken = secureConnectionNode.get(TOKEN_ATTRIBUTE, null);
      secureConnectionNode.put(TOKEN_ATTRIBUTE, token, true);
      secureConnectionsNode.flush();
      return !Objects.equals(previousToken, token);
    } catch (StorageException | IOException e) {
      throw new IllegalStateException("Unable to store connection credentials in secure storage: " + e.getMessage(), e);
    }
  }

  public void removeConnection(ConnectionFacade connection) {
    var connectionNodeName = getConnectionNodeName(connection.getId());
    try {
      var rootNode = getSonarLintPreferenceNode();
      var connectionsNode = rootNode.node(PREF_CONNECTIONS);
      if (connectionsNode.nodeExists(connectionNodeName)) {
        // No need to notify listener for every deleted property
        ((IEclipsePreferences) connectionsNode.node(connectionNodeName)).removePreferenceChangeListener(connectedEngineChangeListener);
        connectionsNode.node(connectionNodeName).removeNode();
        connectionsNode.flush();
      }
    } catch (BackingStoreException e) {
      throw new IllegalStateException("Unable to save connection list", e);
    }
    tryRemoveSecureProperties(connectionNodeName);
  }

  private static void tryRemoveSecureProperties(String connectionNodeName) {
    var secureConnectionsNode = getSecureConnectionsNode();
    if (secureConnectionsNode.nodeExists(connectionNodeName)) {
      secureConnectionsNode.node(connectionNodeName).removeNode();
    }
  }

  /**
   * Returns an array containing all connections.
   *
   * @return an array containing all connections
   */
  public List<ConnectionFacade> getConnections() {
    return List.copyOf(facadesByConnectionId.values());
  }

  /** Checks if there is at least one connection to a SonarCloud project */
  public boolean checkForSonarCloud() {
    return facadesByConnectionId.values().stream().anyMatch(facade -> facade.isSonarCloud());
  }

  /**
   * Returns the connection with the given id.
   *
   * @param id a connection id
   * @return a connection or empty
   */
  public Optional<ConnectionFacade> findById(String id) {
    return Optional.ofNullable(facadesByConnectionId.get(Objects.requireNonNull(id)));
  }

  public List<ConnectionFacade> findByUrl(String serverUrl) {
    return facadesByConnectionId.values().stream()
      .filter(facade -> equalsIgnoringTrailingSlash(facade.getHost(), serverUrl))
      .collect(Collectors.toList());
  }

  private static boolean equalsIgnoringTrailingSlash(String aUrl, String anotherUrl) {
    try {
      return new URI(StringUtils.removeEnd(aUrl, "/")).equals(new URI(StringUtils.removeEnd(anotherUrl, "/")));
    } catch (URISyntaxException e) {
      // should never happen at this stage
      SonarLintLogger.get().error("Malformed server URL", e);
      return false;
    }
  }

  public Optional<ResolvedBinding> resolveBinding(ISonarLintProject project) {
    return resolveBinding(project, SonarLintCorePlugin.loadConfig(project));
  }

  public Optional<ResolvedBinding> resolveBinding(ISonarLintProject project, SonarLintProjectConfiguration config) {
    return config
      .getProjectBinding()
      .flatMap(b -> {
        var connection = findById(b.getConnectionId());
        if (connection.isEmpty()) {
          SonarLintLogger.get().error("Project '" + project.getName() + "' binding refers to an unknown connection: '" + b.getConnectionId()
            + "'. Please fix project binding or unbind project.");
          return Optional.empty();
        }
        return Optional.of(new ResolvedBinding(config.getProjectBinding().get(), connection.get()));
      });
  }

  public void updateConnection(ConnectionFacade facade, String token) {
    if (!facadesByConnectionId.containsKey(facade.getId())) {
      throw new IllegalStateException("There is no connection with id '" + facade.getId() + "'");
    }

    var rootNode = getSonarLintPreferenceNode();
    try {
      if (!rootNode.nodeExists(PREF_CONNECTIONS)) {
        // User is probably editing a default connection. So we have to make them persistent.
        var defaultServers = new ArrayList<>(facadesByConnectionId.values());
        facadesByConnectionId.clear();
        for (ConnectionFacade iServer : defaultServers) {
          addConnection(iServer, "");
        }
      }
    } catch (BackingStoreException e) {
      throw new IllegalStateException("Unable to save connection", e);
    }

    addOrUpdateProperties(facade);
    var credentialsChanged = false;
    if (facade.hasAuth()) {
      credentialsChanged = storeCredentials(facade, token);
    }
    var connectionToUpdate = facadesByConnectionId.get(facade.getId());
    update(connectionToUpdate, facade.getHost(), facade.getOrganization(), facade.getSonarCloudRegion(), facade.hasAuth(), facade.areNotificationsDisabled());

    fireConnectionEvent(connectionToUpdate, EVENT_CHANGED);
    if (credentialsChanged) {
      fireCredentialsChangedEvent(connectionToUpdate);
    }
  }

  private static void fireCredentialsChangedEvent(ConnectionFacade connection) {
    SonarLintBackendService.get().credentialsChanged(connection);
  }

  private void addOrUpdateProperties(ConnectionFacade facade) {
    var rootNode = getSonarLintPreferenceNode();
    var connectionsNode = rootNode.node(PREF_CONNECTIONS);
    var connectionNode = (IEclipsePreferences) connectionsNode.node(getConnectionNodeName(facade.getId()));
    try {
      connectionNode.removePreferenceChangeListener(connectedEngineChangeListener);
      connectionNode.put(URL_ATTRIBUTE, facade.getHost());
      if (StringUtils.isNotBlank(facade.getOrganization())) {
        connectionNode.put(ORG_ATTRIBUTE, facade.getOrganization());
        if (StringUtils.isNotBlank(facade.getSonarCloudRegion())) {
          connectionNode.put(REGION_ATTRIBUTE, facade.getSonarCloudRegion());
        }
      } else {
        connectionNode.remove(ORG_ATTRIBUTE);
        connectionNode.remove(REGION_ATTRIBUTE);
      }
      connectionNode.putBoolean(AUTH_ATTRIBUTE, facade.hasAuth());
      if (facade.areNotificationsDisabled()) {
        connectionNode.putBoolean(NOTIFICATIONS_DISABLED_ATTRIBUTE, true);
      } else {
        connectionNode.remove(NOTIFICATIONS_DISABLED_ATTRIBUTE);
      }
      connectionsNode.flush();
    } catch (BackingStoreException e) {
      throw new IllegalStateException("Unable to save connection list", e);
    } finally {
      connectionNode.addPreferenceChangeListener(connectedEngineChangeListener);
    }
  }

  @Nullable
  public static String getToken(ConnectionFacade facade) throws StorageException {
    return getFromSecure(facade, TOKEN_ATTRIBUTE);
  }

  @Nullable
  private static String getFromSecure(ConnectionFacade facade, String attribute) throws StorageException {
    var connectionNodeName = getConnectionNodeName(facade.getId());
    var secureConnectionsNode = getSecureConnectionsNode();
    if (!secureConnectionsNode.nodeExists(connectionNodeName)) {
      return null;
    }
    var secureConnectionNode = secureConnectionsNode.node(connectionNodeName);
    return secureConnectionNode.get(attribute, null);
  }

  private static ISecurePreferences getSecureConnectionsNode() {
    return SecurePreferencesFactory.getDefault().node(SonarLintCorePlugin.PLUGIN_ID).node(ConnectionManager.PREF_CONNECTIONS);
  }

  private static String getConnectionNodeName(String connectionId) {
    // Should not contain any "/"
    return StringUtils.urlEncode(connectionId);
  }

  @Nullable
  public String validate(String connectionId, boolean editExisting) {
    if (StringUtils.isBlank(connectionId)) {
      return "Connection name must be specified";
    }
    if (!editExisting && facadesByConnectionId.containsKey(connectionId)) {
      return "Connection name already exists";
    }
    return null;
  }

  public ConnectionFacade create(String id, String url, @Nullable String organization, @Nullable String sonarCloudRegion, String token, boolean notificationsEnabled) {
    return update(new ConnectionFacade(id), url, organization, sonarCloudRegion, StringUtils.isNotBlank(token), notificationsEnabled);
  }

  private static ConnectionFacade update(ConnectionFacade facade, String url, @Nullable String organization,
    @Nullable String sonarCloudRegion, boolean hasAuth, boolean notificationsDisabled) {
    return facade.setHost(url)
      .setOrganization(organization)
      .setSonarCloudRegion(sonarCloudRegion)
      .setHasAuth(hasAuth)
      .setNotificationsDisabled(notificationsDisabled);
  }

}
