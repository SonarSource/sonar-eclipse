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
package org.sonar.ide.eclipse.core.internal.markers;

import java.util.Date;
import java.util.Map;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.QualifiedName;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.sonar.ide.eclipse.common.issues.ISonarIssue;
import org.sonar.ide.eclipse.common.servers.ISonarServer;
import org.sonar.ide.eclipse.core.internal.PreferencesUtils;
import org.sonar.ide.eclipse.core.internal.SonarCorePlugin;
import org.sonar.ide.eclipse.core.internal.resources.ISonarProject;
import org.sonar.ide.eclipse.core.internal.resources.SonarProject;
import org.sonar.ide.eclipse.wsclient.WSClientFactory;

public final class MarkerUtils {

  public static final String SONAR_MARKER_RULE_KEY_ATTR = "rulekey";
  public static final String SONAR_MARKER_RULE_NAME_ATTR = "rulename";
  public static final String SONAR_MARKER_ISSUE_SEVERITY_ATTR = "sonarseverity";
  public static final String SONAR_MARKER_ISSUE_ID_ATTR = "issueId";
  public static final String SONAR_MARKER_IS_NEW_ATTR = "isnew";
  public static final String SONAR_MARKER_ASSIGNEE = "assignee";
  public static final String SONAR_MARKER_ASSIGNEE_NAME = "assigneename";

  public static final QualifiedName MODIFICATION_STAMP_PERSISTENT_PROP_KEY = new QualifiedName(SonarCorePlugin.PLUGIN_ID, "modificationStamp");
  public static final QualifiedName LAST_ANALYSIS_DATE_PERSISTENT_PROP_KEY = new QualifiedName(SonarCorePlugin.PLUGIN_ID, "lastAnalysisDate");
  public static final QualifiedName LOCALLY_ANALYSED_PROP_KEY = new QualifiedName(SonarCorePlugin.PLUGIN_ID, "locallyAnalysed");

  private MarkerUtils() {
  }

  public static void createMarkersForJSONIssues(Map<String, IResource> resourcesByKey, Map<String, String> ruleByKey, Map<String, String> userNameByLogin, JSONArray issues) {
    for (Object issueObj : issues) {
      JSONObject jsonIssue = (JSONObject) issueObj;
      String componentKey = ObjectUtils.toString(jsonIssue.get("component"));
      if (resourcesByKey.containsKey(componentKey)) {
        boolean isNew = Boolean.TRUE.equals(jsonIssue.get("isNew")); //$NON-NLS-1$
        try {
          SonarMarker.create(resourcesByKey.get(componentKey), isNew, new SonarIssueFromJsonReport(jsonIssue, ruleByKey, userNameByLogin));
        } catch (CoreException e) {
          SonarCorePlugin.getDefault().error(e.getMessage(), e);
        }
      }
    }
  }

  private static class SonarIssueFromJsonReport implements ISonarIssue {

    private JSONObject jsonIssue;
    private Map<String, String> ruleByKey;
    private Map<String, String> userNameByLogin;

    public SonarIssueFromJsonReport(JSONObject jsonIssue, Map<String, String> ruleByKey, Map<String, String> userNameByLogin) {
      this.jsonIssue = jsonIssue;
      this.ruleByKey = ruleByKey;
      this.userNameByLogin = userNameByLogin;
    }

    @Override
    public String key() {
      return ObjectUtils.toString(jsonIssue.get("key")); //$NON-NLS-1$
    }

    @Override
    public String resourceKey() {
      return ObjectUtils.toString(jsonIssue.get("component")); //$NON-NLS-1$
    }

    @Override
    public boolean resolved() {
      return StringUtils.isNotBlank(ObjectUtils.toString(jsonIssue.get("resolution"))); //$NON-NLS-1$
    }

    @Override
    public Integer line() {
      Long line = (Long) jsonIssue.get("line");//$NON-NLS-1$
      return line != null ? line.intValue() : null;
    }

    @Override
    public String severity() {
      return ObjectUtils.toString(jsonIssue.get("severity"));//$NON-NLS-1$
    }

    @Override
    public String message() {
      return ObjectUtils.toString(jsonIssue.get("message"));//$NON-NLS-1$
    }

    @Override
    public String ruleKey() {
      return ObjectUtils.toString(jsonIssue.get("rule"));//$NON-NLS-1$
    }

    @Override
    public String ruleName() {
      return ObjectUtils.toString(ruleByKey.get(ruleKey()));
    }

    @Override
    public String assigneeLogin() {
      return ObjectUtils.toString(jsonIssue.get("assignee"));//$NON-NLS-1$
    }

    @Override
    public String assigneeName() {
      String login = ObjectUtils.toString(jsonIssue.get("assignee"));//$NON-NLS-1$
      String name = userNameByLogin.get(login);
      return name != null ? name : login;
    }

  }

  /**
   * Test if the given resource need update of its markers
   */
  public static boolean needRefresh(IResource resource, ISonarProject sonarProject, ISonarServer sonarServer) {
    return resourceModifiedSinceLastRefresh(resource) || newAnalysisAvailableSinceLastRefresh(resource, sonarProject, sonarServer);
  }

  private static boolean resourceModifiedSinceLastRefresh(IResource resource) {
    try {
      String previousModificationStampStr = resource.getPersistentProperty(MODIFICATION_STAMP_PERSISTENT_PROP_KEY);
      long previousModificationStamp = previousModificationStampStr != null ? Long.valueOf(previousModificationStampStr) : -1;
      long currentModificationStamp = resource.getModificationStamp();
      if (previousModificationStamp != currentModificationStamp) {
        return true;
      }
    } catch (CoreException e) {
      return true;
    }
    return false;
  }

  private static boolean newAnalysisAvailableSinceLastRefresh(IResource resource, ISonarProject sonarProject, ISonarServer sonarServer) {
    try {
      String previousAnalysisDateStr = resource.getPersistentProperty(LAST_ANALYSIS_DATE_PERSISTENT_PROP_KEY);
      long previousAnalysisDate = previousAnalysisDateStr != null ? Long.valueOf(previousAnalysisDateStr) : -1;
      if (sonarServer.disabled()) {
        return false;
      }
      Date lastAnalysisDateOnServer = WSClientFactory.getSonarClient(sonarServer).getLastAnalysisDate(sonarProject.getKey());
      if (lastAnalysisDateOnServer == null) {
        return false;
      }
      if (previousAnalysisDate != lastAnalysisDateOnServer.getTime()) {
        return true;
      }
    } catch (CoreException e) {
      return true;
    }
    return false;
  }

  public static void updatePersistentProperties(IFile resource, SonarProject sonarProject, ISonarServer sonarServer) {
    try {
      resource.setPersistentProperty(MODIFICATION_STAMP_PERSISTENT_PROP_KEY, "" + resource.getModificationStamp());
      if (sonarServer.disabled()) {
        return;
      }
      Date lastAnalysisDate = WSClientFactory.getSonarClient(sonarServer).getLastAnalysisDate(sonarProject.getKey());
      if (lastAnalysisDate != null) {
        resource.setPersistentProperty(LAST_ANALYSIS_DATE_PERSISTENT_PROP_KEY, "" + lastAnalysisDate.getTime());
      }
    } catch (CoreException e) {
      SonarCorePlugin.getDefault().error("Unable to update persistent properties", e);
    }
  }

  public static void markResourceAsLocallyAnalysed(IResource resource) {
    try {
      resource.setPersistentProperty(LOCALLY_ANALYSED_PROP_KEY, "true");
    } catch (CoreException e) {
      SonarCorePlugin.getDefault().error("Unable to update persistent properties", e);
    }
  }

  public static boolean isResourceLocallyAnalysed(IResource resource) {
    try {
      return "true".equals(resource.getPersistentProperty(LOCALLY_ANALYSED_PROP_KEY));
    } catch (CoreException e) {
      SonarCorePlugin.getDefault().error("Unable to update persistent properties", e);
      return false;
    }
  }

  public static void deleteIssuesMarkers(IResource resource) {
    try {
      resource.deleteMarkers(SonarCorePlugin.MARKER_ID, true, IResource.DEPTH_INFINITE);
      deletePersistentProperties(resource);
    } catch (CoreException e) {
      SonarCorePlugin.getDefault().error(e.getMessage(), e);
    }
  }

  private static void deletePersistentProperties(IResource resource) throws CoreException {
    resource.accept(new IResourceProxyVisitor() {

      @Override
      public boolean visit(IResourceProxy proxy) throws CoreException {
        if (proxy.getType() == IResource.FILE) {
          IResource resource = proxy.requestResource();
          resource.setPersistentProperty(MODIFICATION_STAMP_PERSISTENT_PROP_KEY, null);
          resource.setPersistentProperty(LAST_ANALYSIS_DATE_PERSISTENT_PROP_KEY, null);
          resource.setPersistentProperty(LOCALLY_ANALYSED_PROP_KEY, null);
          return false;
        }
        return true;
      }
    }, IResource.NONE);
  }

  public static void updateAllSonarMarkerSeverity() throws CoreException {
    for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
      if (project.isAccessible()) {
        for (IMarker marker : project.findMarkers(SonarCorePlugin.MARKER_ID, true, IResource.DEPTH_INFINITE)) {
          boolean isNew = marker.getAttribute(SONAR_MARKER_IS_NEW_ATTR, false);
          marker.setAttribute(IMarker.SEVERITY, isNew ? PreferencesUtils.getMarkerSeverityNewIssues() : PreferencesUtils.getMarkerSeverity());
        }
      }
    }
  }

}
