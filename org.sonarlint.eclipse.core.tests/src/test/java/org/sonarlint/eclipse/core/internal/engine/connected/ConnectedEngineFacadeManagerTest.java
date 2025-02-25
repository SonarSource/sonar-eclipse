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
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.sonarlint.eclipse.core.internal.engine.connected.ConnectionManager.AUTH_ATTRIBUTE;
import static org.sonarlint.eclipse.core.internal.engine.connected.ConnectionManager.ORG_ATTRIBUTE;
import static org.sonarlint.eclipse.core.internal.engine.connected.ConnectionManager.PREF_CONNECTIONS;
import static org.sonarlint.eclipse.core.internal.engine.connected.ConnectionManager.TOKEN_ATTRIBUTE;
import static org.sonarlint.eclipse.core.internal.engine.connected.ConnectionManager.URL_ATTRIBUTE;

/**
 * To have this test pass when launched from the IDE, you have to set -pluginCustomization argument (see pom.xml).
 */
public class ConnectedEngineFacadeManagerTest {

  private static final ISecurePreferences ROOT_SECURE = SecurePreferencesFactory.getDefault().node(SonarLintCorePlugin.PLUGIN_ID);
  private static final IEclipsePreferences ROOT = InstanceScope.INSTANCE.getNode(SonarLintCorePlugin.PLUGIN_ID);
  private ConnectionManager manager;

  @Before
  public void prepare() throws Exception {
    manager = SonarLintCorePlugin.getConnectionManager();
    ROOT.node(PREF_CONNECTIONS).removeNode();
    ROOT_SECURE.node(PREF_CONNECTIONS).removeNode();
    manager.stop();
    manager.init();
  }

  @Test
  public void roundTripUsingPublicMethods() throws Exception {
    assertThat(ROOT.nodeExists(PREF_CONNECTIONS)).isFalse();
    assertThat(ROOT_SECURE.nodeExists(PREF_CONNECTIONS)).isFalse();
    var id = "foo/bar";
    var connection = manager.create(id, "http://foo", "bar", "EU", "token", false);
    manager.addConnection(connection, "token");
    assertThat(manager.getConnections()).containsExactly(connection);
    assertThat(manager.findById(id)).contains(connection);
    assertThat(ConnectionManager.getToken(connection)).isEqualTo("token");
    assertThat(ROOT.nodeExists(PREF_CONNECTIONS)).isTrue();
    assertThat(ROOT_SECURE.nodeExists(PREF_CONNECTIONS)).isTrue();
    assertThat(ROOT.node(PREF_CONNECTIONS).nodeExists("foo%2Fbar")).isTrue();
    assertThat(ROOT_SECURE.node(PREF_CONNECTIONS).nodeExists("foo%2Fbar")).isTrue();
    assertThat(ROOT.node(PREF_CONNECTIONS).node("foo%2Fbar").get(URL_ATTRIBUTE, null)).isEqualTo("http://foo");
    assertThat(ROOT.node(PREF_CONNECTIONS).node("foo%2Fbar").getBoolean(AUTH_ATTRIBUTE, false)).isTrue();
    assertThat(ROOT.node(PREF_CONNECTIONS).node("foo%2Fbar").get(ORG_ATTRIBUTE, null)).isEqualTo("bar");
    assertThat(ROOT_SECURE.node(PREF_CONNECTIONS).node("foo%2Fbar").get(TOKEN_ATTRIBUTE, null)).isEqualTo("token");

    var connectionUpdated = manager.create(id, "http://foo2", "bar2", "EU", "token2", false);
    try {
      manager.addConnection(connectionUpdated, "token2");
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalStateException.class).hasMessage("There is already a connection with id '" + id + "'");
    }

    manager.updateConnection(connectionUpdated, "token2");
    assertThat(manager.getConnections()).containsExactly(connection);
    assertThat(manager.findById(id)).contains(connection);
    assertThat(ROOT.node(PREF_CONNECTIONS).node("foo%2Fbar").get(URL_ATTRIBUTE, null)).isEqualTo("http://foo2");
    assertThat(ROOT.node(PREF_CONNECTIONS).node("foo%2Fbar").getBoolean(AUTH_ATTRIBUTE, false)).isTrue();
    assertThat(ROOT.node(PREF_CONNECTIONS).node("foo%2Fbar").get(ORG_ATTRIBUTE, null)).isEqualTo("bar2");
    assertThat(ROOT_SECURE.node(PREF_CONNECTIONS).node("foo%2Fbar").get(TOKEN_ATTRIBUTE, null)).isEqualTo("token2");

    manager.removeConnection(connectionUpdated);
    assertThat(manager.getConnections()).isEmpty();
    assertThat(ROOT.node(PREF_CONNECTIONS).nodeExists("foo%2Fbar")).isFalse();
    assertThat(ROOT_SECURE.node(PREF_CONNECTIONS).nodeExists("foo%2Fbar")).isFalse();
  }

  @Test
  public void should_use_defaults_from_plugin_customization() throws Exception {
    assertThat(ROOT.nodeExists(PREF_CONNECTIONS)).isFalse();
    assertThat(manager.getConnections()).hasSize(1);
    var iServer = manager.findById("default").get();
    assertThat(iServer.getId()).isEqualTo("default");
    assertThat(iServer.getOrganization()).isEqualTo("myOrg");
    assertThat(iServer.hasAuth()).isTrue();
    assertThat(ROOT.nodeExists(PREF_CONNECTIONS)).isFalse();
  }

  @Test
  public void edit_defaults_from_plugin_customization() throws Exception {
    assertThat(ROOT.nodeExists(PREF_CONNECTIONS)).isFalse();
    assertThat(manager.getConnections()).hasSize(1);
    var iServer = manager.findById("default").get();
    assertThat(iServer.getId()).isEqualTo("default");

    manager.updateConnection(manager.create("default", "http://foo2", "bar2", "US", "toto", false), "toto");
    iServer = manager.findById("default").get();
    assertThat(iServer.getId()).isEqualTo("default");
    assertThat(iServer.getHost()).isEqualTo("http://foo2");
    assertThat(iServer.hasAuth()).isTrue();
  }

  @Test
  public void test_listeners() {
    var removed = new ArrayList<ConnectionFacade>();
    var changed = new ArrayList<ConnectionFacade>();
    var added = new ArrayList<ConnectionFacade>();
    addListener(removed, changed, added);

    var defaultServer = manager.findById("default").get();
    manager.removeConnection(defaultServer);
    assertThat(manager.getConnections()).isEmpty();
    assertThat(removed).containsExactly(defaultServer);
    assertThat(changed).isEmpty();
    assertThat(added).isEmpty();

    removed.clear();

    var id = "foo/bar";
    var newServer = manager.create(id, "http://foo", "bar", "EU", "token", false);
    manager.addConnection(newServer, "token");
    assertThat(removed).isEmpty();
    assertThat(changed).isEmpty();
    assertThat(added).containsExactly(newServer);

    added.clear();

    var connectionUpdated = manager.create(id, "http://foo2", "bar2", "US", "token2", false);
    manager.updateConnection(connectionUpdated, "token2");
    assertThat(removed).isEmpty();
    assertThat(changed).containsExactly(connectionUpdated);
    assertThat(added).isEmpty();
  }

  // SLE-63 Emulate external changes
  @Test
  public void test_external_changes() throws Exception {
    var defaultServer = manager.findById("default").get();
    manager.removeConnection(defaultServer);

    var removed = new ArrayList<ConnectionFacade>();
    var changed = new ArrayList<ConnectionFacade>();
    var added = new ArrayList<ConnectionFacade>();
    addListener(removed, changed, added);

    ROOT.node(PREF_CONNECTIONS).node("foo%2Fbar").put(URL_ATTRIBUTE, "http://foo");
    ROOT.node("another").removeNode();
    assertThat(removed).isEmpty();
    assertThat(changed).isEmpty();
    assertThat(added).hasSize(1);

    added.clear();

    var connection = manager.findById("foo/bar").get();

    ROOT.node(PREF_CONNECTIONS).node("foo%2Fbar").put(URL_ATTRIBUTE, "http://foo:9000");
    ROOT.node(PREF_CONNECTIONS).node("foo%2Fbar").putBoolean(AUTH_ATTRIBUTE, true);
    assertThat(removed).isEmpty();
    assertThat(changed).hasSize(2);
    assertThat(added).isEmpty();

    changed.clear();

    ROOT.node(PREF_CONNECTIONS).node("foo%2Fbar").put(ORG_ATTRIBUTE, "bar");
    assertThat(removed).isEmpty();
    assertThat(changed).hasSize(1);
    assertThat(added).isEmpty();

    changed.clear();

    assertThat(manager.getConnections()).hasSize(1);
    assertThat(connection.getId()).isEqualTo("foo/bar");
    assertThat(connection.getHost()).isEqualTo("http://foo:9000");
    assertThat(connection.getOrganization()).isEqualTo("bar");
    assertThat(connection.hasAuth()).isTrue();

    ROOT.node(PREF_CONNECTIONS).removeNode();
    assertThat(manager.getConnections()).hasSize(1);
    assertThat(manager.findById("default")).isPresent();

    assertThat(removed).hasSize(1);
    assertThat(removed.get(0).getId()).isEqualTo("foo/bar");
    assertThat(changed).isEmpty();
    assertThat(added).hasSize(1);
    assertThat(added.get(0).getId()).isEqualTo("default");
  }

  @Test
  public void test_external_changes_on_existing_servers() throws Exception {
    var defaultConnection = manager.findById("default").get();
    manager.removeConnection(defaultConnection);

    manager.stop();

    // Add a connection entry like if it was existing previously
    ROOT.node(PREF_CONNECTIONS).node("foo%2Fbar").put(URL_ATTRIBUTE, "http://foo");

    manager.init();

    var removed = new ArrayList<ConnectionFacade>();
    var changed = new ArrayList<ConnectionFacade>();
    var added = new ArrayList<ConnectionFacade>();
    addListener(removed, changed, added);

    var connection = manager.findById("foo/bar").get();
    assertThat(connection.getId()).isEqualTo("foo/bar");
    assertThat(connection.getHost()).isEqualTo("http://foo");

    ROOT.node(PREF_CONNECTIONS).node("foo%2Fbar").put(URL_ATTRIBUTE, "http://foo:9000");
    ROOT.node(PREF_CONNECTIONS).node("foo%2Fbar").putBoolean(AUTH_ATTRIBUTE, true);
    assertThat(removed).isEmpty();
    assertThat(changed).hasSize(2);
    assertThat(added).isEmpty();

    changed.clear();

    assertThat(manager.getConnections()).hasSize(1);
    assertThat(connection.getId()).isEqualTo("foo/bar");
    assertThat(connection.getHost()).isEqualTo("http://foo:9000");
  }

  @Test
  public void should_ignore_case_for_scheme_and_host_when_finding_connection() {
    var connection = manager.create("ID", "http://foo", "bar", "EU", "token", false);
    manager.addConnection(connection, "token");

    var facades = manager.findByUrl("HTTP://FOO");
    assertThat(facades).contains(connection);
  }

  private void addListener(List<ConnectionFacade> removed, List<ConnectionFacade> changed, List<ConnectionFacade> added) {
    manager.addConnectionManagerListener(new IConnectionManagerListener() {

      @Override
      public void connectionRemoved(ConnectionFacade facade) {
        removed.add(facade);
      }

      @Override
      public void connectionChanged(ConnectionFacade facade) {
        changed.add(facade);
      }

      @Override
      public void connectionAdded(ConnectionFacade facade) {
        added.add(facade);
      }
    });
  }

}
