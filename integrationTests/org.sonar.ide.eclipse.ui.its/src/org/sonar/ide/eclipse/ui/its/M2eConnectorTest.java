/*
 * SonarQube Eclipse
 * Copyright (C) 2010-2013 SonarSource
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package org.sonar.ide.eclipse.ui.its;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.Test;
import org.sonar.ide.eclipse.core.internal.SonarNature;
import org.sonar.ide.eclipse.core.internal.resources.ISonarProject;
import org.sonar.ide.eclipse.core.internal.resources.SonarProject;
import org.sonar.ide.eclipse.ui.its.bots.ImportMavenProjectBot;
import org.sonar.ide.eclipse.ui.its.bots.ImportProjectBot;
import org.sonar.ide.eclipse.ui.its.bots.JavaPackageExplorerBot;
import org.sonar.ide.eclipse.ui.its.utils.JobHelpers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class M2eConnectorTest extends AbstractSQEclipseUITest {
  private static final String PROJECT_NAME = "reference";

  @Test
  public void automaticAssociationWhenEnablingMavenNature() throws Exception {
    new ImportProjectBot(bot).setPath(getProjectPath(PROJECT_NAME)).finish();

    new JavaPackageExplorerBot(bot)
      .expandAndSelect(PROJECT_NAME)
      .clickContextMenu("Configure", "Convert to Maven Project");

    JobHelpers.waitForJobsToComplete(bot);

    IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT_NAME);
    assertThat(SonarNature.hasSonarNature(project), is(true));
    ISonarProject sonarProject = SonarProject.getInstance(project);
    assertThat(sonarProject, is(notNullValue()));
    assertThat(sonarProject.getKey(), is("org.sonar-ide.tests:reference"));
    // SONARIDE-355
    assertThat(sonarProject.getExtraProperties().get(0).getName(), is("sonar.sampleProperty"));
    assertThat(sonarProject.getExtraProperties().get(0).getValue(), is("value"));
  }

  // SONARIDE-369
  @Test
  public void automaticAssociationWhenImportingExistingMavenProject() throws Exception {
    new ImportMavenProjectBot(bot).setPath(getProjectPath("java/java-maven-simple")).finish();

    IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("example-java-maven");
    assertThat(SonarNature.hasSonarNature(project), is(true));
    ISonarProject sonarProject = SonarProject.getInstance(project);
    assertThat(sonarProject, is(notNullValue()));
    assertThat(sonarProject.getKey(), is("org.codehaus.sonar:example-java-maven"));
  }

}
