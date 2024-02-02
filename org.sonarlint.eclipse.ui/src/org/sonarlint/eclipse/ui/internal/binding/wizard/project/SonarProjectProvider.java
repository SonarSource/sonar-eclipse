/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2024 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal.binding.wizard.project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalProvider;
import org.eclipse.jface.wizard.WizardPage;
import org.sonarsource.sonarlint.core.serverapi.component.ServerProject;

public class SonarProjectProvider implements IContentProposalProvider {

  private final ProjectBindingModel model;
  private final WizardPage parentPage;

  public SonarProjectProvider(ProjectBindingModel model, WizardPage parentPage) {
    this.model = model;
    this.parentPage = parentPage;
  }

  @Override
  public IContentProposal[] getProposals(String contents, int position) {
    var list = new ArrayList<IContentProposal>();
    var projectIndex = model.getProjectIndex();
    var filtered = projectIndex != null ? projectIndex.search(contents) : Collections.<ServerProject, Double>emptyMap();
    if (filtered.isEmpty()) {
      parentPage.setMessage("No results", IMessageProvider.INFORMATION);
    } else {
      parentPage.setMessage("", IMessageProvider.NONE);
    }
    var entries = new ArrayList<>(filtered.entrySet());
    entries.sort(
      Comparator.comparing(Map.Entry<ServerProject, Double>::getValue).reversed()
        .thenComparing(Comparator.comparing(e -> e.getKey().getName(), String.CASE_INSENSITIVE_ORDER)));
    for (var entry : entries) {
      list.add(new ProjectContentProposal(entry.getKey()));
    }
    return list.toArray(new IContentProposal[list.size()]);
  }

  public static class ProjectContentProposal implements IContentProposal {

    private final ServerProject sonarProject;

    public ProjectContentProposal(ServerProject sonarProject) {
      this.sonarProject = sonarProject;
    }

    @Override
    public String getContent() {
      return sonarProject.getKey();
    }

    @Override
    public int getCursorPosition() {
      return sonarProject.getKey().length();
    }

    @Override
    public String getLabel() {
      return sonarProject.getName();
    }

    public ServerProject getRemoteProject() {
      return sonarProject;
    }

    @Override
    public String getDescription() {
      return new StringBuilder()
        .append("Name: ").append(sonarProject.getName()).append("\n")
        .append("Key: ").append(sonarProject.getKey()).append("\n")
        .toString();
    }

  }

}
