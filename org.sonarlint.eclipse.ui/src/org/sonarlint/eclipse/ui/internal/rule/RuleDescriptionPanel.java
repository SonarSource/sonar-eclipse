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
package org.sonarlint.eclipse.ui.internal.rule;

import java.util.Objects;
import java.util.regex.Pattern;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.sonarlint.eclipse.core.internal.extension.SonarLintExtensionTracker;
import org.sonarlint.eclipse.core.internal.utils.StringUtils;
import org.sonarlint.eclipse.ui.internal.util.SonarLintWebView;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleMonolithicDescriptionDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.RuleSplitDescriptionDto;

/**
 *  Rule description (differs based on rule type / combination):
 *  ============================================================
 *
 *  -> monolithic description
 *  -> split description:
 *     -> introduction (optional)
 *     -> tab view
 *        -> tab description
 *  -> split description:
 *     -> introduction (optional)
 *     -> tab view
 *        -> context view
 *           -> context description
 */
public class RuleDescriptionPanel extends Composite {
  private final boolean useEditorFontSize;

  public RuleDescriptionPanel(Composite parent, boolean useEditorFontSize) {
    super(parent, SWT.NONE);
    setLayout(new GridLayout(1, false));

    this.useEditorFontSize = useEditorFontSize;
  }

  public void updateRule(Either<RuleMonolithicDescriptionDto, RuleSplitDescriptionDto> description, String languageKey) {
    if (description.isLeft()) {
      // monolithic description
      parseHTMLIntoElements(description.getLeft().getHtmlContent(), this, languageKey);
    } else {
      // split description
      var ruleDescription = description.getRight();

      var intro = ruleDescription.getIntroductionHtmlContent();
      if (StringUtils.isNotBlank(intro)) {
        // introduction (optional)
        parseHTMLIntoElements(intro, this, languageKey);
      }

      // tab view
      var tabFolder = new TabFolder(this, SWT.NONE);
      tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
      for (var tab : ruleDescription.getTabs()) {
        var tabItem = new TabItem(tabFolder, SWT.NONE);
        tabItem.setText(tab.getTitle());
        var tabContent = new Composite(tabFolder, SWT.NONE);
        tabContent.setLayout(new GridLayout(1, false));

        var content = tab.getContent();
        if (content.isLeft()) {
          // tab description
          parseHTMLIntoElements(content.getLeft().getHtmlContent(), tabContent, languageKey);
        } else {
          // context view
          var contextualDescription = content.getRight();
          var contextualTabFolder = new TabFolder(tabContent, SWT.NONE);
          for (var contextualTab : contextualDescription.getContextualSections()) {
            // context description
            var contextualTabItem = new TabItem(contextualTabFolder, SWT.NONE);
            contextualTabItem.setText(contextualTab.getDisplayName());
            var contextualTabContent = new Composite(contextualTabFolder, SWT.NONE);
            tabContent.setLayout(new GridLayout(1, false));

            if (Objects.equals(contextualDescription.getDefaultContextKey(), contextualTab.getContextKey())) {
              contextualTabFolder.setSelection(contextualTabItem);
            }

            parseHTMLIntoElements(contextualTab.getHtmlContent(), contextualTabContent, languageKey);
            contextualTabItem.setControl(contextualTabContent);
          }
          contextualTabFolder.requestLayout();
        }
        tabItem.setControl(tabContent);
      }
      tabFolder.requestLayout();
    }
    requestLayout();
  }

  private void parseHTMLIntoElements(String html, Composite parent, String languageKey) {
    var currentHTML = html;
    var matcherStart = Pattern.compile("<pre[^>]*>").matcher(currentHTML);
    var matcherEnd = Pattern.compile("</pre>").matcher(currentHTML);

    while (matcherStart.find() && matcherEnd.find()) {
      var front = currentHTML.substring(0, matcherStart.start()).trim();
      if (!front.isEmpty() && !front.isBlank()) {
        var frontFragment = new SonarLintWebView(parent, useEditorFontSize);
        var gridData = new GridData();
        gridData.horizontalAlignment = SWT.FILL;
        gridData.grabExcessHorizontalSpace = true;
        frontFragment.setLayoutData(gridData);

        frontFragment.setHtmlBody(front);
      }

      var middle = currentHTML.substring(matcherStart.end(), matcherEnd.start()).trim();
      if (!middle.isEmpty() && !middle.isBlank()) {
        createSourceViewer(StringUtils.xmlDecode(middle), parent, languageKey);
      }

      currentHTML = currentHTML.substring(matcherEnd.end(), currentHTML.length()).trim();
      matcherStart = Pattern.compile("<pre[^>]*>").matcher(currentHTML);
      matcherEnd = Pattern.compile("</pre>").matcher(currentHTML);
    }

    if (!currentHTML.isEmpty() && !currentHTML.isBlank()) {
      var endFragment = new SonarLintWebView(parent, useEditorFontSize);
      var gridData = new GridData();
      gridData.horizontalAlignment = SWT.FILL;
      gridData.grabExcessHorizontalSpace = true;
      endFragment.setLayoutData(gridData);

      endFragment.setHtmlBody(currentHTML);
    }
  }

  private static void createSourceViewer(String html, Composite parent, String languageKey) {
    // Configure the syntax highlighting based on the rule language key and if a configuration and document partitioner
    // is provided by any plug-in via the extension mechanism.
    // INFO: Configuration must extend of org.eclipse.jface.text.source.SourceViewerConfiguration
    // INFO: Document partitioner must implement org.eclipse.jface.text.IDocumentPartitioner
    var configurationProviders = SonarLintExtensionTracker.getInstance().getSyntaxHighlightingProvider();
    SourceViewerConfiguration sourceViewerConfigurationNullable = null;
    for (var configurationProvider : configurationProviders) {
      var sourceViewerConfigurationOptional = configurationProvider.sourceViewerConfiguration(languageKey);
      if (sourceViewerConfigurationOptional.isPresent()) {
        sourceViewerConfigurationNullable = sourceViewerConfigurationOptional.get();
        break;
      }
    }

    IDocumentPartitioner documentPartitionerNullable = null;
    for (var configurationProvider : configurationProviders) {
      var documentPartitionerOptional = configurationProvider.documentPartitioner(languageKey);
      if (documentPartitionerOptional.isPresent()) {
        documentPartitionerNullable = documentPartitionerOptional.get();
        break;
      }
    }

    var snippetElement = new SourceViewer(parent, null, SWT.BORDER | SWT.H_SCROLL);
    var gridData = new GridData();
    gridData.horizontalAlignment = SWT.FILL;
    gridData.grabExcessHorizontalSpace = true;
    gridData.horizontalIndent = 10;
    snippetElement.getTextWidget().setLayoutData(gridData);

    var content = new Document(html);
    if (sourceViewerConfigurationNullable != null && documentPartitionerNullable != null) {
      content.setDocumentPartitioner(
        sourceViewerConfigurationNullable.getConfiguredDocumentPartitioning(snippetElement),
        documentPartitionerNullable);
      content.setDocumentPartitioner(documentPartitionerNullable);
      documentPartitionerNullable.connect(content);
    }

    if (sourceViewerConfigurationNullable != null) {
      snippetElement.configure(sourceViewerConfigurationNullable);
    }

    snippetElement.setDocument(content);
    snippetElement.setEditable(false);
  }
}
