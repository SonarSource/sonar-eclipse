/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2018 SonarSource SA
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
package org.sonarlint.eclipse.ui.internal.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ICheckStateProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TreeNodeContentProvider;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.sonarlint.eclipse.ui.internal.util.SonarLintRuleBrowser;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;

// Inspired by: http://www.vogella.com/tutorials/EclipseJFaceTree/article.html
public class RulesConfigurationPart {

  private final Map<String, List<RuleDetailsWrapper>> ruleDetailsWrappersByLanguage;

  private final RuleDetailsWrapperComparator comparator = new RuleDetailsWrapperComparator();

  private final RuleDetailsWrapperFilter filter = new RuleDetailsWrapperFilter();

  private CheckboxTreeViewer viewer;

  private SonarLintRuleBrowser ruleBrowser;

  public RulesConfigurationPart(Collection<RuleDetails> allRuleDetails, Collection<RuleKey> excluded, Collection<RuleKey> included) {
    this.ruleDetailsWrappersByLanguage = allRuleDetails.stream()
      .sorted(Comparator.comparing(RuleDetails::getKey))
      .map(rd -> new RuleDetailsWrapper(rd, excluded, included))
      .collect(Collectors.groupingBy(w -> w.ruleDetails.getLanguage(), Collectors.toList()));
  }

  protected void createControls(Composite parent) {
    Composite pageComponent = new Composite(parent, SWT.NULL);
    GridLayout layout = new GridLayout(1, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    pageComponent.setLayout(layout);

    createFilterPart(pageComponent);

    Composite treeComposite = new Composite(pageComponent, SWT.NONE);
    GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
    treeComposite.setLayoutData(gridData);
    createTreeViewer(treeComposite);

    ruleBrowser = new SonarLintRuleBrowser(pageComponent);
    ruleBrowser.setLayoutData(gridData);
  }

  private void createFilterPart(Composite parent) {
    ComboViewer combo = new ComboViewer(parent, SWT.READ_ONLY);
    combo.setContentProvider(ArrayContentProvider.getInstance());
    combo.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        if (element instanceof Type) {
          Type type = (Type) element;
          return type.label;
        }
        return super.getText(element);
      }
    });
    combo.setInput(Type.values());
    combo.setSelection(new StructuredSelection(Type.ALL));
    ISelectionChangedListener selectionChangedListener = event -> {
      IStructuredSelection selection = (IStructuredSelection) event.getSelection();
      if (selection.size() > 0) {
        filter.setType((Type) selection.getFirstElement());
        viewer.refresh();
      }
    };
    combo.addSelectionChangedListener(selectionChangedListener);
  }

  private void createTreeViewer(Composite parent) {
    viewer = new CheckboxTreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
    viewer.setContentProvider(new ViewContentProvider());
    viewer.getTree().setHeaderVisible(true);
    viewer.setComparator(comparator);
    viewer.addFilter(filter);

    TreeViewerColumn languageColumn = new TreeViewerColumn(viewer, SWT.NONE);
    languageColumn.getColumn().setWidth(100);
    languageColumn.setLabelProvider(new DelegatingStyledCellLabelProvider(new LanguageLabelProvider()));

    TreeViewerColumn ruleNameColumn = new TreeViewerColumn(viewer, SWT.NONE);
    ruleNameColumn.getColumn().setText("Rule name");
    ruleNameColumn.getColumn().setWidth(300);
    ruleNameColumn.setLabelProvider(new DelegatingStyledCellLabelProvider(new RuleNameLabelProvider()));
    ruleNameColumn.getColumn().addSelectionListener(newSelectionAdapter(1));
    ruleNameColumn.getColumn().setMoveable(true);

    TreeViewerColumn ruleKeyColumn = new TreeViewerColumn(viewer, SWT.NONE);
    ruleKeyColumn.getColumn().setText("Rule key");
    ruleKeyColumn.getColumn().setWidth(100);
    ruleKeyColumn.setLabelProvider(new DelegatingStyledCellLabelProvider(new RuleKeyLabelProvider()));
    ruleKeyColumn.getColumn().addSelectionListener(newSelectionAdapter(2));
    ruleKeyColumn.getColumn().setMoveable(true);

    TreeColumnLayout treeLayout = new TreeColumnLayout();
    treeLayout.setColumnData(languageColumn.getColumn(), new ColumnPixelData(70));
    treeLayout.setColumnData(ruleNameColumn.getColumn(), new ColumnWeightData(100));
    treeLayout.setColumnData(ruleKeyColumn.getColumn(), new ColumnWeightData(50));
    parent.setLayout(treeLayout);

    viewer.setInput(ruleDetailsWrappersByLanguage.keySet().toArray(new String[ruleDetailsWrappersByLanguage.size()]));

    ISelectionChangedListener selectionChangedListener = event -> {
      IStructuredSelection thisSelection = (IStructuredSelection) event.getSelection();
      Object selectedNode = thisSelection.getFirstElement();
      if (selectedNode instanceof RuleDetailsWrapper) {
        RuleDetailsWrapper wrapper = (RuleDetailsWrapper) selectedNode;
        ruleBrowser.updateRule(wrapper.ruleDetails);
      }
    };
    viewer.addSelectionChangedListener(selectionChangedListener);

    viewer.addCheckStateListener(new RuleCheckStateListener());

    viewer.setCheckStateProvider(new RuleCheckStateProvider());

    createContextMenu();
  }

  private void createContextMenu() {
    MenuManager contextMenu = new MenuManager("#ViewerMenu"); //$NON-NLS-1$
    contextMenu.setRemoveAllWhenShown(true);
    contextMenu.addMenuListener(this::fillContextMenu);
    Menu menu = contextMenu.createContextMenu(viewer.getControl());
    viewer.getControl().setMenu(menu);
  }

  private void fillContextMenu(IMenuManager contextMenu) {
    contextMenu.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));

    contextMenu.add(new Action("Activate") {
      @Override
      public void run() {
        setActiveForSelection(true);
      }
    });

    contextMenu.add(new Action("Deactivate") {
      @Override
      public void run() {
        setActiveForSelection(false);
      }
    });
  }

  private void setActiveForSelection(boolean isActive) {
    Iterator<?> iterator = ((IStructuredSelection) viewer.getSelection()).iterator();
    while (iterator.hasNext()) {
      setActiveForElement(iterator.next(), isActive);
    }
  }

  private void setActiveForElement(Object element, boolean isActive) {
    if (element instanceof RuleDetailsWrapper) {
      RuleDetailsWrapper wrapper = (RuleDetailsWrapper) element;
      wrapper.isActive = isActive;
      viewer.refresh(element);
    } else if (element instanceof String) {
      String language = (String) element;
      ruleDetailsWrappersByLanguage.get(language).stream().forEach(w -> w.isActive = isActive);
      viewer.refresh();
    }
  }

  private SelectionAdapter newSelectionAdapter(int index) {
    return new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent event) {
        comparator.setColumn(index);
        int direction = comparator.getDirection();
        viewer.getTree().setSortColumn((TreeColumn) event.getSource());
        viewer.getTree().setSortDirection(direction);
        viewer.refresh();
      }
    };
  }

  enum Type {
    ALL("All rules", w -> true),
    ACTIVE("Active rules", w -> w.isActive),
    INACTIVE("Inactive rules", w -> !w.isActive),
    CHANGED("Changed rules", w -> w.isActive != w.ruleDetails.isActiveByDefault());

    final String label;
    final Predicate<RuleDetailsWrapper> predicate;

    Type(String label, Predicate<RuleDetailsWrapper> predicate) {
      this.label = label;
      this.predicate = predicate;
    }
  }

  private class RuleDetailsWrapperFilter extends ViewerFilter {
    private Type type = Type.ALL;

    private void setType(Type type) {
      this.type = type;
    }

    @Override
    public boolean select(Viewer viewer, Object parentElement, Object element) {
      if (element instanceof RuleDetailsWrapper) {
        return select((RuleDetailsWrapper) element);
      }

      if (element instanceof String) {
        return select((String) element);
      }

      return false;
    }

    private boolean select(String language) {
      return ruleDetailsWrappersByLanguage.get(language).stream().anyMatch(type.predicate);
    }

    private boolean select(RuleDetailsWrapper wrapper) {
      return type.predicate.test(wrapper);
    }
  }

  private static class RuleDetailsWrapperComparator extends ViewerComparator {
    private int index;
    private static final int DESCENDING = 1;
    private int direction = DESCENDING;

    public RuleDetailsWrapperComparator() {
      this.index = 0;
      direction = DESCENDING;
    }

    public int getDirection() {
      return direction == 1 ? SWT.DOWN : SWT.UP;
    }

    public void setColumn(int column) {
      if (column == this.index) {
        // Same column as last sort; toggle the direction
        direction = 1 - direction;
      } else {
        // New column; do an ascending sort
        this.index = column;
        direction = DESCENDING;
      }
    }

    @Override
    public int compare(Viewer viewer, Object e1, Object e2) {
      if (!(e1 instanceof RuleDetailsWrapper && e2 instanceof RuleDetailsWrapper)) {
        return 0;
      }
      RuleDetailsWrapper w1 = (RuleDetailsWrapper) e1;
      RuleDetailsWrapper w2 = (RuleDetailsWrapper) e2;
      int rc = 0;
      switch (index) {
        case 1:
          rc = w1.ruleDetails.getName().compareTo(w2.ruleDetails.getName());
          break;
        case 2:
          rc = w1.ruleDetails.getKey().compareTo(w2.ruleDetails.getKey());
          break;
        default:
          rc = 0;
      }
      // If descending order, flip the direction
      if (direction == DESCENDING) {
        rc = -rc;
      }
      return rc;
    }
  }

  private class RuleCheckStateListener implements ICheckStateListener {
    @Override
    public void checkStateChanged(CheckStateChangedEvent event) {
      Object element = event.getElement();
      if (element instanceof RuleDetailsWrapper) {
        RuleDetailsWrapper wrapper = (RuleDetailsWrapper) element;
        wrapper.isActive = event.getChecked();
        viewer.refresh(element);
      } else if (element instanceof String) {
        String language = (String) element;
        viewer.setExpandedState(element, true);
        ruleDetailsWrappersByLanguage.get(language).stream()
          .filter(filter.type.predicate)
          .forEach(w -> w.isActive = event.getChecked());
        viewer.refresh();
      }
    }
  }

  private class RuleCheckStateProvider implements ICheckStateProvider {
    @Override
    public boolean isGrayed(Object element) {
      if (element instanceof RuleDetailsWrapper) {
        return false;
      }
      if (element instanceof String) {
        String language = (String) element;
        boolean foundActive = false;
        boolean foundInActive = false;
        for (RuleDetailsWrapper wrapper : ruleDetailsWrappersByLanguage.get(language)) {
          if (!filter.type.predicate.test(wrapper)) {
            continue;
          }
          if (wrapper.isActive) {
            foundActive = true;
          } else {
            foundInActive = true;
          }

          // stop scanning after found both kinds
          if (foundActive && foundInActive) {
            break;
          }
        }
        return foundActive == foundInActive;
      }
      return false;
    }

    @Override
    public boolean isChecked(Object element) {
      if (element instanceof RuleDetailsWrapper) {
        RuleDetailsWrapper wrapper = (RuleDetailsWrapper) element;
        return wrapper.isActive;
      }
      if (element instanceof String) {
        String language = (String) element;
        return ruleDetailsWrappersByLanguage.get(language).stream()
          .filter(filter.type.predicate)
          .anyMatch(w -> w.isActive);
      }
      return false;
    }
  }

  private class ViewContentProvider extends TreeNodeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
      return (String[]) inputElement;
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      if (!(parentElement instanceof String)) {
        return new Object[0];
      }
      String language = (String) parentElement;
      List<RuleDetailsWrapper> list = ruleDetailsWrappersByLanguage.get(language);
      return list.toArray(new RuleDetailsWrapper[list.size()]);
    }

    @Override
    public Object getParent(Object element) {
      if (element instanceof RuleDetailsWrapper) {
        RuleDetailsWrapper wrapper = (RuleDetailsWrapper) element;
        return wrapper.ruleDetails.getLanguage();
      }
      return null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return element instanceof String;
    }
  }

  private static class LanguageLabelProvider extends LabelProvider implements IStyledLabelProvider {
    @Override
    public StyledString getStyledText(Object element) {
      if (element instanceof String) {
        String language = (String) element;
        return new StyledString(language);
      }
      return new StyledString();
    }
  }

  private static class RuleKeyLabelProvider extends LabelProvider implements IStyledLabelProvider {
    @Override
    public StyledString getStyledText(Object element) {
      if (element instanceof RuleDetailsWrapper) {
        RuleDetailsWrapper wrapper = (RuleDetailsWrapper) element;
        return new StyledString(wrapper.ruleDetails.getKey());
      }
      return new StyledString();
    }
  }

  private static class RuleNameLabelProvider extends LabelProvider implements IStyledLabelProvider {
    @Override
    public StyledString getStyledText(Object element) {
      if (element instanceof RuleDetailsWrapper) {
        RuleDetailsWrapper wrapper = (RuleDetailsWrapper) element;
        return new StyledString(wrapper.ruleDetails.getName());
      }
      return new StyledString();
    }
  }

  private static class RuleDetailsWrapper {
    private final RuleDetails ruleDetails;
    private boolean isActive;

    RuleDetailsWrapper(RuleDetails ruleDetails, Collection<RuleKey> excluded, Collection<RuleKey> included) {
      this.ruleDetails = ruleDetails;
      this.isActive = computeIsActive(ruleDetails.getKey(), ruleDetails.isActiveByDefault(), excluded, included);
    }

    private static boolean computeIsActive(String key, boolean activeByDefault, Collection<RuleKey> excluded, Collection<RuleKey> included) {
      RuleKey ruleKey = RuleKey.parse(key);
      return !excluded.contains(ruleKey) && (activeByDefault || included.contains(ruleKey));
    }
  }

  // visible for testing
  public static class ExclusionsAndInclusions {
    private final Collection<RuleKey> excluded;
    private final Collection<RuleKey> included;

    public ExclusionsAndInclusions(Collection<RuleKey> excluded, Collection<RuleKey> included) {
      this.excluded = excluded;
      this.included = included;
    }

    public Collection<RuleKey> excluded() {
      return excluded;
    }

    public Collection<RuleKey> included() {
      return included;
    }
  }

  // visible for testing
  public ExclusionsAndInclusions computeExclusionsAndInclusions() {
    Collection<RuleKey> excluded = new ArrayList<>();
    Collection<RuleKey> included = new ArrayList<>();
    ruleDetailsWrappersByLanguage.entrySet().stream()
      .flatMap(e -> e.getValue().stream())
      .filter(w -> w.isActive != w.ruleDetails.isActiveByDefault())
      .forEach(w -> {
        RuleKey ruleKey = RuleKey.parse(w.ruleDetails.getKey());
        if (w.isActive) {
          included.add(ruleKey);
        } else {
          excluded.add(ruleKey);
        }
      });

    return new ExclusionsAndInclusions(excluded, included);
  }

  // visible for testing
  public void resetToDefaults() {
    ruleDetailsWrappersByLanguage.entrySet().stream()
      .flatMap(e -> e.getValue().stream())
      .forEach(w -> w.isActive = w.ruleDetails.isActiveByDefault());
  }

  void refresh() {
    viewer.refresh();
  }
}
