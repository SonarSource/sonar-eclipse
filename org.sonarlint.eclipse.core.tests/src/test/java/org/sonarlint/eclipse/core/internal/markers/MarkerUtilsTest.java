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
package org.sonarlint.eclipse.core.internal.markers;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.Position;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sonarlint.eclipse.core.internal.resources.SonarLintProject;
import org.sonarlint.eclipse.tests.common.SonarTestCase;

import static org.assertj.core.api.Assertions.assertThat;

public class MarkerUtilsTest extends SonarTestCase {

  private static IProject project;

  @BeforeClass
  public static void importProject() throws Exception {
    project = importEclipseProject("SimpleProject");
    // Configure the project
    SonarLintProject.getInstance(project);
  }

  @Test
  public void testLineStartEnd() throws Exception {
    try (TextFileContext context = new TextFileContext(project.getFile("src/main/java/ViolationOnFile.java"))) {
      TextRange textRange = new TextRange(2);
      Position position = MarkerUtils.getPosition(context.getDocument(), textRange);
      assertThat(position.getOffset()).isEqualTo(31);
      assertThat(position.getLength()).isEqualTo(32);
      assertThat(context.getDocument().get(position.getOffset(), position.getLength())).isEqualTo("  public static String INSTANCE;");
    }
  }

  @Test
  public void testLineStartEndCrLf() throws Exception {
    try (TextFileContext context = new TextFileContext(project.getFile("src/main/java/ViolationOnFileCrLf.java"))) {
      TextRange textRange = new TextRange(2);
      Position position = MarkerUtils.getPosition(context.getDocument(), textRange);
      assertThat(position.getOffset()).isEqualTo(32);
      assertThat(position.getLength()).isEqualTo(32);
      assertThat(context.getDocument().get(position.getOffset(), position.getLength())).isEqualTo("  public static String INSTANCE;");
    }
  }

  @Test
  public void testPreciseIssueLocationSingleLine() throws Exception {
    try (TextFileContext context = new TextFileContext(project.getFile("src/main/java/ViolationOnFile.java"))) {
      TextRange textRange = new TextRange(2, 23, 2, 31);
      Position position = MarkerUtils.getPosition(context.getDocument(), textRange);
      assertThat(position.getOffset()).isEqualTo(54);
      assertThat(position.getLength()).isEqualTo(8);
      assertThat(context.getDocument().get(position.getOffset(), position.getLength())).isEqualTo("INSTANCE");
    }
  }

  @Test
  public void testPreciseIssueLocationMultiLine() throws Exception {
    try (TextFileContext context = new TextFileContext(project.getFile("src/main/java/ViolationOnFile.java"))) {
      TextRange textRange = new TextRange(4, 34, 5, 12);
      Position position = MarkerUtils.getPosition(context.getDocument(), textRange);
      assertThat(position.getOffset()).isEqualTo(101);
      assertThat(position.getLength()).isEqualTo(18);
      assertThat(context.getDocument().get(position.getOffset(), position.getLength())).isEqualTo("\"foo\"\n     + \"bar\"");
    }
  }

  @Test
  public void testNonexistentLine() throws Exception {
    try (TextFileContext context = new TextFileContext(project.getFile("src/main/java/ViolationOnFile.java"))) {
      int nonexistentLine = context.getDocument().getNumberOfLines() + 1;
      Position position = MarkerUtils.getPosition(context.getDocument(), nonexistentLine);
      assertThat(position).isNull();
    }
  }

  @Test
  public void testNonexistentTextRange() throws Exception {
    try (TextFileContext context = new TextFileContext(project.getFile("src/main/java/ViolationOnFile.java"))) {
      int nonexistentLine = context.getDocument().getNumberOfLines() + 1;
      TextRange textRange = new TextRange(nonexistentLine, 5, nonexistentLine, 12);
      Position position = MarkerUtils.getPosition(context.getDocument(), textRange);
      assertThat(position).isNull();
    }
  }

  @Test
  public void testTextRangeWithoutLine() throws Exception {
    try (TextFileContext context = new TextFileContext(project.getFile("src/main/java/ViolationOnFile.java"))) {
      Position position = MarkerUtils.getPosition(context.getDocument(), new TextRange(null));
      assertThat(position).isNull();
    }
  }
}
