/*
 * SonarLint for Eclipse ITs
 * Copyright (C) 2009-2020 SonarSource SA
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
package org.sonarlint.eclipse.its;

import java.util.stream.Stream;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sonarlint.eclipse.its.bots.JavaPackageExplorerBot;
import org.sonarlint.eclipse.its.bots.OnTheFlyViewBot;
import org.sonarlint.eclipse.its.utils.JobHelpers;
import org.sonarlint.eclipse.its.utils.SwtBotUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class SecondaryLocationsTest extends AbstractSonarLintTest {

  private static OnTheFlyViewBot onTheFlyBot;
  private SWTBotView onTheFly;

  @BeforeClass
  public static void openSampleProjectAndFileWithFlows() throws Exception {
    SwtBotUtils.openPerspective(bot, JavaUI.ID_PERSPECTIVE);
    importEclipseProject("java/java-multiple-flows", "java-multiple-flows");
    JobHelpers.waitForJobsToComplete(bot);

    onTheFlyBot = new OnTheFlyViewBot(bot);
  }

  @Before
  public void showOnTheFlyView() {
    onTheFly = onTheFlyBot.show();
  }

  @After
  public void closeActiveEditor() {
    bot.activeEditor().close();
    JobHelpers.waitForJobsToComplete(bot);
  }

  @Test
  public void shouldShowMultipleFlows() throws Exception {

    JavaPackageExplorerBot explorerBot = new JavaPackageExplorerBot(bot);
    explorerBot.expandAndDoubleClick("java-multiple-flows", "src", "hello", "MultiFlows.java");
    JobHelpers.waitForJobsToComplete(bot);

    SWTBotEclipseEditor helloEditor = bot.editorByTitle("MultiFlows.java").toTextEditor();
    helloEditor.setFocus();

    String issueTitle = "\"NullPointerException\" will be thrown when invoking method \"doAnotherThingWith()\".";
    waitUntilOnTheFlyViewHasItemWithTitle(issueTitle + " [+2 flows]");
    onTheFly.bot().tree().getAllItems()[0].select();

    SWTBotView issueLocationsView = getIssueLocationsView();

    SWTBotTreeItem[] allItems = issueLocationsView.bot().tree().getAllItems();
    assertThat(allItems).hasSize(1);

    SWTBotTreeItem locationRoot = allItems[0];
    assertThat(locationRoot.getText()).isEqualTo(issueTitle);
    assertThat(locationRoot.getItems()).hasSize(2);

    SWTBotTreeItem flow1 = locationRoot.getNode("Flow 1");
    assertThat(flow1.getItems()).hasSize(5);

    SWTBotTreeItem flow2 = locationRoot.getNode("Flow 2");
    assertThat(flow2.getItems()).hasSize(5);

    // Flows are not ordered, we can only check that the first nodes do not point to the same location

    flow1.getItems()[0].doubleClick();
    assertThat(helloEditor.getSelection()).isEqualTo("arg = null");
    int flow1Line = helloEditor.cursorPosition().line;

    flow2.getItems()[0].doubleClick();
    assertThat(helloEditor.getSelection()).isEqualTo("arg = null");
    int flow2Line = helloEditor.cursorPosition().line;

    assertThat(flow1Line).isNotEqualTo(flow2Line);
  }

  @Test
  public void shouldShowFlattenedFlows() throws Exception {

    JavaPackageExplorerBot explorerBot = new JavaPackageExplorerBot(bot);
    explorerBot.expandAndDoubleClick("java-multiple-flows", "src", "hello", "CognitiveComplexity.java");
    JobHelpers.waitForJobsToComplete(bot);

    SWTBotEclipseEditor cognitiveComplexityEditor = bot.editorByTitle("CognitiveComplexity.java").toTextEditor();
    cognitiveComplexityEditor.setFocus();

    String issueTitle = "Refactor this method to reduce its Cognitive Complexity from 24 to the 15 allowed.";
    waitUntilOnTheFlyViewHasItemWithTitle(issueTitle + " [+15 locations]");
    onTheFly.bot().tree().getAllItems()[0].select();

    SWTBotView issueLocationsView = getIssueLocationsView();

    SWTBotTreeItem[] allItems = issueLocationsView.bot().tree().getAllItems();
    assertThat(allItems).hasSize(1);

    SWTBotTreeItem locationRoot = allItems[0];
    assertThat(locationRoot.getText()).isEqualTo(issueTitle);
    SWTBotTreeItem[] allNodes = locationRoot.getItems();
    assertThat(allNodes).hasSize(15);

    allNodes[0].doubleClick();
    assertThat(cognitiveComplexityEditor.getSelection()).isEqualTo("if");
    assertThat(cognitiveComplexityEditor.cursorPosition()).extracting(p -> p.line, p -> p.column).containsExactly(18, 6);

    allNodes[14].doubleClick();
    assertThat(cognitiveComplexityEditor.getSelection()).isEqualTo("else");
    assertThat(cognitiveComplexityEditor.cursorPosition()).extracting(p -> p.line, p -> p.column).containsExactly(45, 12);
  }

  private SWTBotView getIssueLocationsView() {
    return bot.viewById("org.sonarlint.eclipse.ui.views.IssueLocationsView");
  }

  public void waitUntilOnTheFlyViewHasItemWithTitle(String expectedTitle) {
    SWTBot otfBot = onTheFly.bot();
    otfBot.waitUntil(new DefaultCondition() {
      @Override
      public boolean test() throws Exception {
        SWTBotTree otfTree = otfBot.tree();
        return otfTree.hasItems() && Stream.of(otfTree.getAllItems()).anyMatch(i -> i.cell(1).equals(expectedTitle)); 
      }
      @Override
      public String getFailureMessage() {
        return "On the fly view not updated";
      }
    }, 10_000L);
  }
}
