/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2023 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;
import org.sonarlint.eclipse.core.internal.TriggerType;
import org.sonarlint.eclipse.core.internal.jobs.AbstractAnalyzeProjectJob;
import org.sonarlint.eclipse.core.internal.jobs.AnalyzeProjectRequest;
import org.sonarlint.eclipse.core.internal.jobs.AnalyzeProjectRequest.FileWithDocument;
import org.sonarlint.eclipse.core.internal.jobs.AnalyzeProjectsJob;
import org.sonarlint.eclipse.core.internal.preferences.SonarLintGlobalConfiguration;
import org.sonarlint.eclipse.core.resource.ISonarLintFile;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarlint.eclipse.ui.internal.util.PlatformUtils;
import org.sonarlint.eclipse.ui.internal.util.SelectionUtils;
import org.sonarsource.sonarlint.core.commons.Language;

public class AnalyzeCommand extends AbstractHandler {

  @Nullable
  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    var selection = HandlerUtil.getCurrentSelectionChecked(event);
    final Map<ISonarLintProject, Collection<FileWithDocument>> filesPerProject;
    if (selection instanceof IStructuredSelection) {
      filesPerProject = findSelectedFilesPerProject(event);
    } else {
      filesPerProject = new HashMap<>(1);
      var editedFile = findEditedFile(event);
      if (editedFile != null) {
        filesPerProject.put(editedFile.getFile().getProject(), List.of(editedFile));
      }
    }

    if (!filesPerProject.isEmpty()) {
      runAnalysisJob(HandlerUtil.getActiveShell(event), filesPerProject);
    }

    return null;
  }

  private static void runAnalysisJob(Shell shell, Map<ISonarLintProject, Collection<FileWithDocument>> filesPerProject) {
    var totalFileCount = filesPerProject.values().stream().mapToInt(Collection::size).sum();
    
    // Asking for a few files (e.g. analyzing a package) is annoying, increasing the threshold in order to not spam
    // pop-ups to the user!
    if (totalFileCount > 10 && !askConfirmation(shell)) {
      return;
    }
    
    var unavailableLanguagesReference = EnumSet.noneOf(Language.class);
    
    if (filesPerProject.size() == 1) {
      var entry = filesPerProject.entrySet().iterator().next();
      var req = new AnalyzeProjectRequest(entry.getKey(), entry.getValue(), TriggerType.MANUAL, true);
      var fileCount = req.getFiles().size();
      String reportTitle;
      if (fileCount == 1) {
        reportTitle = "File " + req.getFiles().iterator().next().getFile().getName();
      } else {
        reportTitle = fileCount + " files of project " + entry.getKey().getName();
      }
      
      var job = AbstractAnalyzeProjectJob.create(req, unavailableLanguagesReference);
      AnalyzeChangeSetCommand.registerJobListener(job, reportTitle, unavailableLanguagesReference);
      job.schedule();
    } else {
      var job = new AnalyzeProjectsJob(filesPerProject, unavailableLanguagesReference);
      AnalyzeChangeSetCommand.registerJobListener(job, "All files of " + filesPerProject.size() + " projects",
        unavailableLanguagesReference);
      job.schedule();
    }
  }

  private static boolean askConfirmation(Shell shell) {
    if (SonarLintGlobalConfiguration.skipConfirmAnalyzeMultipleFiles()) {
      return true;
    }

    // Note: in oxygen and later another overload exists that allows setting custom button labels
    var dialog = MessageDialogWithToggle.open(
      MessageDialog.CONFIRM, shell, "Confirmation",
      "Analyzing multiple files may take a long time to complete. "
        + "To get the best from SonarLint, you should preferably use the on-the-fly analysis for the files you're working on."
        + "\n\nWould you like to proceed?",
      "Always proceed without asking", false, null, null, SWT.NONE);

    var proceed = dialog.getReturnCode() == 0;

    if (proceed && dialog.getToggleState()) {
      SonarLintGlobalConfiguration.setSkipConfirmAnalyzeMultipleFiles();
    }

    return proceed;
  }

  protected Map<ISonarLintProject, Collection<FileWithDocument>> findSelectedFilesPerProject(ExecutionEvent event) throws ExecutionException {
    var filesToAnalyzePerProject = new LinkedHashMap<ISonarLintProject, Collection<FileWithDocument>>();
    for (var file : SelectionUtils.allSelectedFiles(HandlerUtil.getCurrentSelectionChecked(event), true)) {
      filesToAnalyzePerProject.putIfAbsent(file.getProject(), new ArrayList<>());
      var editorPart = PlatformUtils.findEditor(file);
      if (editorPart instanceof ITextEditor) {
        var doc = ((ITextEditor) editorPart).getDocumentProvider().getDocument(editorPart.getEditorInput());
        filesToAnalyzePerProject.get(file.getProject()).add(new FileWithDocument(file, doc));
      } else {
        filesToAnalyzePerProject.get(file.getProject()).add(new FileWithDocument(file, null));
      }
    }
    return filesToAnalyzePerProject;
  }

  @Nullable
  static FileWithDocument findEditedFile(ExecutionEvent event) {
    var activeEditor = HandlerUtil.getActiveEditor(event);
    if (activeEditor == null) {
      return null;
    }
    var input = activeEditor.getEditorInput();
    if (input instanceof IFileEditorInput) {
      var doc = ((ITextEditor) activeEditor).getDocumentProvider().getDocument(activeEditor.getEditorInput());
      var file = ((IFileEditorInput) input).getFile();
      var sonarLintFile = Adapters.adapt(file, ISonarLintFile.class);
      return sonarLintFile != null ? new FileWithDocument(sonarLintFile, doc) : null;
    }
    return null;
  }

}
