/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2021 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal.hotspots;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.stream.Stream;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.ui.internal.SonarLintImages;
import org.sonarlint.eclipse.ui.internal.SonarLintUiPlugin;
import org.sonarlint.eclipse.ui.internal.util.LocationsUtils;
import org.sonarlint.eclipse.ui.internal.util.SonarLintWebView;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;

public class HotspotsView extends ViewPart {

  private static final String NO_SECURITY_HOTSPOTS_SELECTED = "<em>No security hotspots selected<em>";
  public static final String ID = SonarLintUiPlugin.PLUGIN_ID + ".views.HotspotsView";
  private PageBook book;
  private Control hotspotsPage;
  private TableViewer hotspotViewer;
  private SashForm splitter;

  private Color highPriorityColor;
  private Color mediumPriorityColor;
  private Color lowPriorityColor;
  private SonarLintWebView riskDescriptionBrowser;
  private SonarLintWebView vulnerabilityDescriptionBrowser;
  private SonarLintWebView fixRecommendationsBrowser;

  @Override
  public void createPartControl(Composite parent) {
    highPriorityColor = new Color(parent.getDisplay(), 212, 51, 63);
    mediumPriorityColor = new Color(parent.getDisplay(), 237, 125, 32);
    lowPriorityColor = new Color(parent.getDisplay(), 234, 190, 6);

    FormToolkit toolkit = new FormToolkit(parent.getDisplay());
    book = new PageBook(parent, SWT.NONE);

    Control noHotspotsMessage = createNoHotspotsMessage(toolkit);
    hotspotsPage = createHotspotsPage(toolkit);
    book.showPage(noHotspotsMessage);

  }

  private Control createNoHotspotsMessage(FormToolkit kit) {
    Form form = kit.createForm(book);
    Composite body = form.getBody();
    GridLayout layout = new GridLayout();
    body.setLayout(layout);

    Link emptyMsg = new Link(body, SWT.CENTER | SWT.WRAP);
    emptyMsg.setText("You can open a Security Hotspot from SonarQube. <a>Learn more</a>");
    GridData gd = new GridData(SWT.LEFT, SWT.FILL, true, false);
    emptyMsg.setLayoutData(gd);
    emptyMsg.setBackground(emptyMsg.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
    emptyMsg.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        try {
          PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL("https://github.com/SonarSource/sonarlint-eclipse/wiki/Security-Hotspots"));
        } catch (PartInitException | MalformedURLException e1) {
          // ignore
        }
      }
    });
    return form;
  }

  private Control createHotspotsPage(FormToolkit kit) {
    Form form = kit.createForm(book);
    Composite body = form.getBody();
    GridLayout layout = new GridLayout();
    body.setLayout(layout);

    splitter = new SashForm(body, SWT.NONE);
    GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
    splitter.setLayoutData(gd);
    splitter.setOrientation(SWT.HORIZONTAL);

    createHotspotTable();

    final TabFolder tabFolder = new TabFolder(splitter, SWT.NONE);

    TabItem riskDescriptionTab = new TabItem(tabFolder, SWT.NONE);
    riskDescriptionTab.setText("What's the risk?");
    riskDescriptionTab.setToolTipText("Risk decription");
    riskDescriptionTab.setControl(createRiskDescriptionControl(tabFolder));

    TabItem vulnerabilityDescriptionTab = new TabItem(tabFolder, SWT.NONE);
    vulnerabilityDescriptionTab.setText("Are you at risk?");
    vulnerabilityDescriptionTab.setToolTipText("Vulnerability decription");
    vulnerabilityDescriptionTab.setControl(createVulnerabilityDescriptionControl(tabFolder));

    TabItem fixRecommendationsTab = new TabItem(tabFolder, SWT.NONE);
    fixRecommendationsTab.setText("How can you fix it?");
    fixRecommendationsTab.setToolTipText("Recommendations");
    fixRecommendationsTab.setControl(createFixRecommendationsControl(tabFolder));

    return form;
  }

  private Control createRiskDescriptionControl(Composite parent) {
    riskDescriptionBrowser = new SonarLintWebView(parent, true) {

      @Override
      protected String body() {
        ServerHotspot hotspot = getSelectedHotspot();
        if (hotspot == null) {
          return NO_SECURITY_HOTSPOTS_SELECTED;
        }
        return hotspot.rule.riskDescription;
      }

    };
    return riskDescriptionBrowser;
  }

  private Control createVulnerabilityDescriptionControl(Composite parent) {
    vulnerabilityDescriptionBrowser = new SonarLintWebView(parent, true) {

      @Override
      protected String body() {
        ServerHotspot hotspot = getSelectedHotspot();
        if (hotspot == null) {
          return NO_SECURITY_HOTSPOTS_SELECTED;
        }
        return hotspot.rule.vulnerabilityDescription;
      }
    };
    return vulnerabilityDescriptionBrowser;
  }

  private Control createFixRecommendationsControl(Composite parent) {
    fixRecommendationsBrowser = new SonarLintWebView(parent, true) {

      @Override
      protected String body() {
        ServerHotspot hotspot = getSelectedHotspot();
        if (hotspot == null) {
          return NO_SECURITY_HOTSPOTS_SELECTED;
        }
        return hotspot.rule.fixRecommendations;
      }
    };
    return fixRecommendationsBrowser;
  }

  @Nullable
  private ServerHotspot getSelectedHotspot() {
    Object firstElement = hotspotViewer.getStructuredSelection().getFirstElement();
    return firstElement != null ? ((HotspotAndMarker) firstElement).hotspot : null;
  }

  private void createHotspotTable() {
    hotspotViewer = new TableViewer(splitter, SWT.H_SCROLL | SWT.V_SCROLL | SWT.HIDE_SELECTION | SWT.FULL_SELECTION | SWT.SINGLE | SWT.READ_ONLY);

    final Table table = hotspotViewer.getTable();
    table.setHeaderVisible(true);
    table.setLinesVisible(true);

    // Deselect line when clicking on a blank space in the table
    table.addListener(SWT.MouseDown, event -> {

      TableItem item = table.getItem(new Point(event.x, event.y));

      if (item == null) {
        // No table item at the click location?
        hotspotViewer.setSelection(StructuredSelection.EMPTY);
      }
    });

    hotspotViewer.addPostSelectionChangedListener(event -> {
      riskDescriptionBrowser.refresh();
      vulnerabilityDescriptionBrowser.refresh();
      fixRecommendationsBrowser.refresh();
    });

    hotspotViewer.addDoubleClickListener(event -> openMarkerOfSelectedHotspot());

    TableViewerColumn colPriority = new TableViewerColumn(hotspotViewer, SWT.NONE);
    colPriority.getColumn().setText("Priority");
    colPriority.getColumn().setResizable(true);
    colPriority.setLabelProvider(new ColumnLabelProvider() {

      @Override
      public Image getImage(Object element) {
        switch (((HotspotAndMarker) element).hotspot.rule.vulnerabilityProbability) {
          case HIGH:
            return SonarLintImages.IMG_HOTSPOT_HIGH;
          case MEDIUM:
            return SonarLintImages.IMG_HOTSPOT_MEDIUM;
          case LOW:
            return SonarLintImages.IMG_HOTSPOT_LOW;
          default:
            throw new IllegalStateException("Unexpected probablility");
        }
      }

      @Nullable
      @Override
      public String getText(Object element) {
        return null;
      }
    });

    TableViewerColumn colDescription = new TableViewerColumn(hotspotViewer, SWT.NONE);
    colDescription.getColumn().setText("Description");
    colDescription.getColumn().setResizable(true);
    colDescription.setLabelProvider(new ColumnLabelProvider() {
      @Override
      public String getText(Object element) {
        boolean locationValid = isLocationValid(element);

        return ((HotspotAndMarker) element).hotspot.message + (locationValid ? "" : " (Local code not matching)");
      }

      private boolean isLocationValid(Object element) {
        boolean locationValid;
        @Nullable
        IMarker marker = ((HotspotAndMarker) element).marker;
        if (marker != null) {
          locationValid = marker.exists() && marker.getAttribute(IMarker.CHAR_START, -1) >= 0;
          ITextEditor editor = LocationsUtils.findOpenEditorFor(marker);
          if (editor != null) {
            Position p = LocationsUtils.getMarkerPosition(marker, editor);
            locationValid = locationValid && p != null && !p.isDeleted();
          }
        } else {
          locationValid = false;
        }
        return locationValid;
      }
    });

    TableViewerColumn colCategory = new TableViewerColumn(hotspotViewer, SWT.NONE);
    colCategory.getColumn().setText("Category");
    colCategory.getColumn().setResizable(true);
    colCategory.setLabelProvider(new ColumnLabelProvider() {
      @Override
      public String getText(Object element) {
        HotspotAndMarker hotspotAndMarker = (HotspotAndMarker) element;
        return SecurityHotspotCategory.findByShortName(hotspotAndMarker.hotspot.rule.securityCategory)
          .map(SecurityHotspotCategory::getLongName)
          .orElse(hotspotAndMarker.hotspot.rule.securityCategory);
      }
    });

    TableViewerColumn colResource = new TableViewerColumn(hotspotViewer, SWT.NONE);
    colResource.getColumn().setText("Resource");
    colResource.getColumn().setResizable(true);
    colResource.setLabelProvider(new ColumnLabelProvider() {
      @Override
      public String getText(Object element) {
        HotspotAndMarker hotspotAndMarker = (HotspotAndMarker) element;
        return hotspotAndMarker.marker != null ? hotspotAndMarker.marker.getResource().getName() : "";
      }
    });

    TableViewerColumn colLine = new TableViewerColumn(hotspotViewer, SWT.NONE);
    colLine.getColumn().setText("Location");
    colLine.getColumn().setResizable(true);
    colLine.setLabelProvider(new ColumnLabelProvider() {
      @Override
      public String getText(Object element) {
        HotspotAndMarker hotspotAndMarker = (HotspotAndMarker) element;
        return "line " + hotspotAndMarker.hotspot.textRange.getStartLine();
      }
    });

    TableViewerColumn colRuleKey = new TableViewerColumn(hotspotViewer, SWT.NONE);
    colRuleKey.getColumn().setText("Rule");
    colRuleKey.getColumn().setResizable(true);
    colRuleKey.setLabelProvider(new ColumnLabelProvider() {
      @Override
      public String getText(Object element) {
        HotspotAndMarker hotspotAndMarker = (HotspotAndMarker) element;
        return hotspotAndMarker.hotspot.rule.key;
      }
    });

    hotspotViewer.setContentProvider(ArrayContentProvider.getInstance());
  }

  public void openHotspot(ServerHotspot hotspot, @Nullable IMarker marker) {
    clearMarkers();

    HotspotAndMarker hotspotAndMarker = new HotspotAndMarker(hotspot, marker);

    book.showPage(hotspotsPage);
    hotspotViewer.setInput(new HotspotAndMarker[] {hotspotAndMarker});
    hotspotViewer.setSelection(new StructuredSelection(hotspotAndMarker));
    hotspotViewer.refresh();
    for (TableColumn c : hotspotViewer.getTable().getColumns()) {
      c.pack();
    }
    splitter.layout();

    openMarkerOfSelectedHotspot();
  }

  private void clearMarkers() {
    HotspotAndMarker[] previous = (HotspotAndMarker[]) hotspotViewer.getInput();
    if (previous != null) {
      Stream.of(previous).forEach(h -> {
        if (h.marker != null) {
          try {
            h.marker.delete();
          } catch (CoreException e) {
            SonarLintLogger.get().error("Unable to delete previous marker", e);
          }
        }
      });
    }
  }

  private void openMarkerOfSelectedHotspot() {
    Object firstElement = hotspotViewer.getStructuredSelection().getFirstElement();
    IMarker marker = firstElement != null ? ((HotspotAndMarker) firstElement).marker : null;
    if (marker != null && marker.exists()) {
      try {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        IDE.openEditor(page, marker);
      } catch (PartInitException e) {
        SonarLintLogger.get().error("Unable to open editor with hotspot", e);
      }
    }
  }

  private static class HotspotAndMarker {
    private final ServerHotspot hotspot;
    @Nullable
    private final IMarker marker;

    public HotspotAndMarker(ServerHotspot hotspot, @Nullable IMarker marker) {
      this.hotspot = hotspot;
      this.marker = marker;
    }
  }

  @Override
  public void setFocus() {
    if (hotspotsPage.isVisible()) {
      hotspotViewer.getTable().setFocus();
    } else {
      book.setFocus();
    }
  }

  @Override
  public void dispose() {
    clearMarkers();
    highPriorityColor.dispose();
    mediumPriorityColor.dispose();
    lowPriorityColor.dispose();
    super.dispose();
  }

}
