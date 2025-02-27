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
package org.sonarlint.eclipse.core.internal.resources;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sonarlint.eclipse.tests.common.SonarTestCase;

import static org.junit.Assert.assertEquals;

public class DefaultSonarLintFileAdapterTest extends SonarTestCase {

  private static IProject project;

  @BeforeClass
  public static void importProject() throws IOException, CoreException {
    project = importEclipseProject("charsets");
  }

  @Test
  public void testKnownCharset() {
    var file = (IFile) project.findMember("known.xml");
    var adapter = new DefaultSonarLintFileAdapter(new DefaultSonarLintProjectAdapter(project), file);
    assertEquals(StandardCharsets.UTF_8, adapter.getCharset());
  }

  @Test
  public void testUnknownCharset() {
    var file = (IFile) project.findMember("unknown.xml");
    var adapter = new DefaultSonarLintFileAdapter(new DefaultSonarLintProjectAdapter(project), file);
    assertEquals(Charset.defaultCharset(), adapter.getCharset());
  }

  @Test
  public void testIllegalCharset() throws CoreException {
    var file = (IFile) project.findMember("illegal.xml");
    var adapter = new DefaultSonarLintFileAdapter(new DefaultSonarLintProjectAdapter(project), file);
    // For illegal encodings Eclipse returns the file's parent default encoding
    assertEquals(Charset.forName(project.getDefaultCharset()), adapter.getCharset());
  }

}
