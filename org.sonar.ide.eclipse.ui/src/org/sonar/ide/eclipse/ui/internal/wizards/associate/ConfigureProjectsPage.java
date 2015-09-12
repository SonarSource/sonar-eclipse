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
package org.sonar.ide.eclipse.ui.internal.wizards.associate;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.databinding.beans.BeanProperties;
import org.eclipse.core.databinding.observable.list.WritableList;
import org.eclipse.core.databinding.property.value.IValueProperty;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.bindings.keys.IKeyLookup;
import org.eclipse.jface.bindings.keys.KeyLookupFactory;
import org.eclipse.jface.databinding.viewers.ViewerSupport;
import org.eclipse.jface.dialogs.DialogPage;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.FocusCellHighlighter;
import org.eclipse.jface.viewers.FocusCellOwnerDrawHighlighter;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TableViewerEditor;
import org.eclipse.jface.viewers.TableViewerFocusCellManager;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;
import org.sonar.ide.eclipse.core.internal.SonarCorePlugin;
import org.sonar.ide.eclipse.core.internal.SonarNature;
import org.sonar.ide.eclipse.core.internal.resources.SonarProject;
import org.sonar.ide.eclipse.core.internal.servers.SonarServer;
import org.sonar.ide.eclipse.ui.internal.SonarImages;
import org.sonar.ide.eclipse.ui.internal.SonarUiPlugin;
import org.sonar.ide.eclipse.wsclient.ConnectionException;
import org.sonar.ide.eclipse.wsclient.ISonarRemoteModule;
import org.sonar.ide.eclipse.wsclient.ISonarServer;
import org.sonar.ide.eclipse.wsclient.WSClientFactory;

public class ConfigureProjectsPage extends WizardPage {

  private final List<IProject> projects;
  private TableViewer viewer;
  private final Collection<SonarServer> sonarServers;
  private boolean alreadyRun = false;

  public ConfigureProjectsPage(List<IProject> projects) {
    super("configureProjects", Messages.ConfigureProjectsPage_title, SonarImages.SONARWIZBAN_IMG); //$NON-NLS-1$
    setDescription(Messages.ConfigureProjectsPage_description);
    this.projects = projects;
    sonarServers = SonarCorePlugin.getServersManager().getServers();
  }

  @Override
  public void createControl(Composite parent) {
    PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, SonarUiPlugin.PLUGIN_ID + ".help_associate");

    Composite container = new Composite(parent, SWT.NONE);

    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    layout.marginHeight = 0;
    layout.marginWidth = 5;
    container.setLayout(layout);

    // List of projects
    viewer = new TableViewer(container, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.VIRTUAL);
    viewer.getTable().setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true, 1, 3));

    viewer.getTable().setHeaderVisible(true);

    TableViewerColumn columnProject = new TableViewerColumn(viewer, SWT.LEFT);
    columnProject.getColumn().setText(Messages.ConfigureProjectsPage_project);
    columnProject.getColumn().setWidth(200);

    TableViewerColumn columnSonarProject = new TableViewerColumn(viewer, SWT.LEFT);
    columnSonarProject.getColumn().setText(Messages.ConfigureProjectsPage_sonar_project);
    columnSonarProject.getColumn().setWidth(600);

    columnSonarProject.setEditingSupport(new ProjectAssociationModelEditingSupport(viewer));

    List<ProjectAssociationModel> list = Lists.newArrayList();
    for (IProject project : projects) {
      ProjectAssociationModel sonarProject = new ProjectAssociationModel(project);
      list.add(sonarProject);
    }

    ColumnViewerEditorActivationStrategy activationSupport = createActivationSupport();

    /*
     * Without focus highlighter, keyboard events will not be delivered to
     * ColumnViewerEditorActivationStragety#isEditorActivationEvent(...) (see above)
     */
    FocusCellHighlighter focusCellHighlighter = new FocusCellOwnerDrawHighlighter(viewer);
    TableViewerFocusCellManager focusCellManager = new TableViewerFocusCellManager(viewer, focusCellHighlighter);

    TableViewerEditor.create(viewer, focusCellManager, activationSupport, ColumnViewerEditor.TABBING_VERTICAL
      | ColumnViewerEditor.KEYBOARD_ACTIVATION);

    ViewerSupport.bind(
      viewer,
      new WritableList(list, ProjectAssociationModel.class),
      new IValueProperty[] {BeanProperties.value(ProjectAssociationModel.class, ProjectAssociationModel.PROPERTY_PROJECT_ECLIPSE_NAME),
        BeanProperties.value(ProjectAssociationModel.class, ProjectAssociationModel.PROPERTY_PROJECT_SONAR_FULLNAME)});

    scheduleAutomaticAssociation();

    setControl(container);
  }

  private ColumnViewerEditorActivationStrategy createActivationSupport() {
    ColumnViewerEditorActivationStrategy activationSupport = new ColumnViewerEditorActivationStrategy(viewer) {
      @Override
      protected boolean isEditorActivationEvent(ColumnViewerEditorActivationEvent event) {
        return event.eventType == ColumnViewerEditorActivationEvent.TRAVERSAL
          || event.eventType == ColumnViewerEditorActivationEvent.MOUSE_CLICK_SELECTION
          || event.eventType == ColumnViewerEditorActivationEvent.PROGRAMMATIC
          || event.eventType == ColumnViewerEditorActivationEvent.KEY_PRESSED && event.keyCode == KeyLookupFactory
            .getDefault().formalKeyLookup(IKeyLookup.F2_NAME);
      }
    };
    activationSupport.setEnableEditorActivationWithKeyboard(true);
    return activationSupport;
  }

  private void scheduleAutomaticAssociation() {
    getShell().addShellListener(new ShellAdapter() {
      @Override
      public void shellActivated(ShellEvent shellevent) {
        if (!alreadyRun) {
          alreadyRun = true;
          try {
            if (sonarServers.isEmpty()) {
              setMessage(Messages.ConfigureProjectsPage_no_servers, IMessageProvider.ERROR);
            } else {

              List<ISonarServer> nonReachableServers = new ArrayList<ISonarServer>();
              for (ISonarServer sonarServer : sonarServers) {
                if (sonarServer.disabled()) {
                  nonReachableServers.add(sonarServer);
                }
              }
              // Provide non-reachable servers detail
              if (!nonReachableServers.isEmpty()) {
                displayErrorMessageWithServers(ConfigureProjectsPage.this, nonReachableServers);
              } else {
                setMessage("", IMessageProvider.NONE);
              }

              getWizard().getContainer().run(true, false, new AssociateProjects(sonarServers, getProjects()));
            }
          } catch (InvocationTargetException ex) {
            if (ex.getTargetException() instanceof ConnectionException) {
              setMessage(Messages.ConfigureProjectsPage_one_of_servers_not_reachable, IMessageProvider.ERROR);
            } else {
              setMessage("Error: " + ex.getMessage(), IMessageProvider.ERROR);
            }
          } catch (Exception ex) {
            SonarCorePlugin.getDefault().error(ex.getMessage(), ex);
            setMessage("Error: " + ex.getMessage(), IMessageProvider.ERROR);
          }
        }
      }
    });
  }

  private class ProjectAssociationModelEditingSupport extends EditingSupport {

    SonarSearchEngineProvider contentProposalProvider = new SonarSearchEngineProvider(sonarServers, ConfigureProjectsPage.this);

    public ProjectAssociationModelEditingSupport(TableViewer viewer) {
      super(viewer);
    }

    @Override
    protected boolean canEdit(Object element) {
      return element instanceof ProjectAssociationModel;
    }

    @Override
    protected CellEditor getCellEditor(Object element) {
      return new TextCellEditorWithContentProposal(viewer.getTable(), contentProposalProvider, (ProjectAssociationModel) element);
    }

    @Override
    protected Object getValue(Object element) {
      return StringUtils.trimToEmpty(((ProjectAssociationModel) element).getSonarProjectName());
    }

    @Override
    protected void setValue(Object element, Object value) {
      // Don't set value as the model was already updated in the text adapter
    }

  }

  /**
   * Update all Eclipse projects when an association was provided:
   *   - enable Sonar nature
   *   - update sonar URL / key
   *   - refresh issues if necessary
   * @return
   */
  public boolean finish() {
    final ProjectAssociationModel[] projectAssociations = getProjects();
    for (ProjectAssociationModel projectAssociation : projectAssociations) {
      if (StringUtils.isNotBlank(projectAssociation.getKey())) {
        boolean changed = false;
        IProject project = projectAssociation.getProject();
        SonarProject sonarProject = SonarProject.getInstance(project);
        if (!projectAssociation.getServerId().equals(sonarProject.getServerId())) {
          sonarProject.setServerId(projectAssociation.getServerId());
          changed = true;
        }
        if (!projectAssociation.getKey().equals(sonarProject.getKey())) {
          sonarProject.setKey(projectAssociation.getKey());
          changed = true;
        }
        if (changed) {
          sonarProject.save();
        }
        if (!SonarNature.hasSonarNature(project)) {
          SonarNature.enableNature(project);
        }
      }
    }
    return true;
  }

  private ProjectAssociationModel[] getProjects() {
    WritableList projectAssociations = (WritableList) viewer.getInput();
    return (ProjectAssociationModel[]) projectAssociations.toArray(new ProjectAssociationModel[projectAssociations.size()]);
  }

  public static class AssociateProjects implements IRunnableWithProgress {

    private final Collection<SonarServer> sonarServers;
    private final ProjectAssociationModel[] projectAssociations;

    public AssociateProjects(Collection<SonarServer> sonarServers, ProjectAssociationModel[] projects) {
      Assert.isNotNull(sonarServers);
      Assert.isNotNull(projects);
      this.sonarServers = sonarServers;
      this.projectAssociations = projects;
    }

    @Override
    public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
      monitor.beginTask(Messages.ConfigureProjectsPage_taskname, IProgressMonitor.UNKNOWN);
      // Retrieve list of all remote projects
      Map<String, List<ISonarRemoteModule>> remoteSonarProjects = fetchAllRemoteSonarModules();

      // Verify that all projects already associated are found on remote. If not found projects are considered as unassociated.
      validateProjectAssociations(remoteSonarProjects);

      // Now check for all potential matches for a all non associated projects on all Sonar servers
      Map<ProjectAssociationModel, List<PotentialMatchForProject>> potentialMatches = findAllPotentialMatches(remoteSonarProjects);

      // Now for each project try to find the better match
      findBestMatchAndAssociate(potentialMatches);

      monitor.done();
    }

    private void findBestMatchAndAssociate(Map<ProjectAssociationModel, List<PotentialMatchForProject>> potentialMatches) {
      for (Map.Entry<ProjectAssociationModel, List<PotentialMatchForProject>> entry : potentialMatches.entrySet()) {
        List<PotentialMatchForProject> potentialMatchesForProject = entry.getValue();
        if (!potentialMatchesForProject.isEmpty()) {
          // Take the better choice according to Levenshtein distance
          PotentialMatchForProject best = potentialMatchesForProject.get(0);
          int currentBestDistance = StringUtils.getLevenshteinDistance(best.getResource().getKey(), entry.getKey().getEclipseName());
          for (PotentialMatchForProject potentialMatch : potentialMatchesForProject) {
            int distance = StringUtils.getLevenshteinDistance(potentialMatch.getResource().getKey(), entry.getKey().getEclipseName());
            if (distance < currentBestDistance) {
              best = potentialMatch;
              currentBestDistance = distance;
            }
          }
          entry.getKey().associate(best.getHost(), best.getResource().getName(), best.getResource().getKey());
        }
      }
    }

    private Map<ProjectAssociationModel, List<PotentialMatchForProject>> findAllPotentialMatches(Map<String, List<ISonarRemoteModule>> remoteSonarProjects) {
      Map<ProjectAssociationModel, List<PotentialMatchForProject>> potentialMatches = new HashMap<ProjectAssociationModel, List<PotentialMatchForProject>>();
      for (Map.Entry<String, List<ISonarRemoteModule>> entry : remoteSonarProjects.entrySet()) {
        String url = entry.getKey();
        List<ISonarRemoteModule> resources = entry.getValue();
        for (ProjectAssociationModel sonarProject : projectAssociations) {
          if (StringUtils.isBlank(sonarProject.getKey())) {
            // Not associated yet
            if (!potentialMatches.containsKey(sonarProject)) {
              potentialMatches.put(sonarProject, new ArrayList<PotentialMatchForProject>());
            }
            for (ISonarRemoteModule resource : resources) {
              // A resource is a potential match if resource key contains Eclipse name
              if (resource.getKey().contains(sonarProject.getEclipseName())) {
                potentialMatches.get(sonarProject).add(new PotentialMatchForProject(resource, url));
              }
            }
          }
        }
      }
      return potentialMatches;
    }

    private void validateProjectAssociations(Map<String, List<ISonarRemoteModule>> remoteSonarProjects) {
      for (ProjectAssociationModel projectAssociation : projectAssociations) {
        if (SonarNature.hasSonarNature(projectAssociation.getProject())) {
          SonarProject sonarProject = SonarProject.getInstance(projectAssociation.getProject());
          String key = sonarProject.getKey();
          String url = sonarProject.getServerId();
          validateProjectAssociation(remoteSonarProjects, projectAssociation, key, url);
        }
      }
    }

    private void validateProjectAssociation(Map<String, List<ISonarRemoteModule>> remoteSonarProjects, ProjectAssociationModel projectAssociation, String key, String url) {
      boolean found = false;
      if (remoteSonarProjects.containsKey(url)) {
        for (ISonarRemoteModule remoteProject : remoteSonarProjects.get(url)) {
          if (remoteProject.getKey().equals(key)) {
            found = true;
            // Call associate to have the name
            projectAssociation.associate(url, remoteProject.getName(), key);
            break;
          }
        }
      }
      if (!found) {
        // There is no Sonar server with the provided URL or not matching project so consider the project is not associated
        projectAssociation.unassociate();
      }
    }

    private Map<String, List<ISonarRemoteModule>> fetchAllRemoteSonarModules() {
      Map<String, List<ISonarRemoteModule>> remoteSonarModules = new HashMap<String, List<ISonarRemoteModule>>();
      for (ISonarServer sonarServer : sonarServers) {
        if (!sonarServer.started()) {
          SonarCorePlugin.getDefault().debug(sonarServer + " seems unreachable\n");
          continue;
        }
        List<ISonarRemoteModule> remoteModules = WSClientFactory.getSonarClient(sonarServer).listAllRemoteModules();
        remoteSonarModules.put(sonarServer.getUrl(), remoteModules);
      }
      return remoteSonarModules;
    }

    private static class PotentialMatchForProject {
      private ISonarRemoteModule resource;
      private String host;

      public PotentialMatchForProject(ISonarRemoteModule resource, String host) {
        super();
        this.resource = resource;
        this.host = host;
      }

      public ISonarRemoteModule getResource() {
        return resource;
      }

      public String getHost() {
        return host;
      }

    }
  }

  /**
   * Displays a user facing error message on the project configuration page with non-reachable servers details.
   *  
   * @param nonReachableServers
   */
  private void displayErrorMessageWithServers(final DialogPage configureProjectsPage, List<ISonarServer> nonReachableServers) {
    final StringBuilder builder = new StringBuilder();
    if (nonReachableServers.size() == sonarServers.size()) {
      // All servers are non-reachable.
      builder.append(Messages.ConfigureProjectsPage_no_live_servers);
    } else {
      final String message = Messages.ConfigureProjectsPage_only_few_servers_live;
      builder.append(message);
      builder.append("\n"); //$NON-NLS-1$
      for (final ISonarServer nonReachableServer : nonReachableServers) {
        builder.append("\t"); //$NON-NLS-1$
        builder.append(nonReachableServer.getUrl());
        final String username = nonReachableServer.getUsername();
        if (!Strings.isNullOrEmpty(username)) {
          builder.append(","); //$NON-NLS-1$
          builder.append(username);
        }
        builder.append("\n"); //$NON-NLS-1$
      }
    }
    builder.append(Messages.ConfigureProjectsPage_check_conn_settings);
    configureProjectsPage.setMessage(builder.toString(), IMessageProvider.ERROR);
  }

}
