/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.component.ArrangementMatchNodeComponentFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 10/31/12 1:23 PM
 */
public class ArrangementMatchingRulesList extends JBList {

  @NotNull private static final Logger LOG            = Logger.getInstance("#" + ArrangementMatchingRulesList.class.getName());
  @NotNull private static final JLabel EMPTY_RENDERER = new JLabel("");

  @NotNull private final TIntObjectHashMap<ArrangementListRowDecorator> myComponents = new TIntObjectHashMap<ArrangementListRowDecorator>();

  @NotNull private final ArrangementMatchNodeComponentFactory myFactory;

  private int myRowUnderMouse = -1;
  private boolean mySkipMouseClick;

  public ArrangementMatchingRulesList(@NotNull ArrangementNodeDisplayManager displayManager,
                                      @NotNull ArrangementColorsProvider colorsProvider,
                                      @NotNull ArrangementStandardSettingsAware settingsFilter)
  {
    super(new ArrangementMatchingRulesListModel());
    myFactory = new ArrangementMatchNodeComponentFactory(displayManager, colorsProvider, this);
    setCellRenderer(new MyListCellRenderer());
    addMouseMotionListener(new MouseAdapter() {
      @Override public void mouseMoved(MouseEvent e) { onMouseMoved(e); }
    });
    addMouseListener(new MouseAdapter() {
      @Override public void mouseExited(MouseEvent e) { onMouseExited(); }
      @Override public void mouseEntered(MouseEvent e) { onMouseEntered(e); }
      @Override public void mouseClicked(MouseEvent e) { onMousePressed(e); }
    });
    addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        onSelectionChange(e);
      }
    });
  }

  @NotNull
  @Override
  public ArrangementMatchingRulesListModel getModel() {
    return (ArrangementMatchingRulesListModel)super.getModel();
  }

  public void setRules(@Nullable List<StdArrangementMatchRule> rules) {
    myComponents.clear();
    getModel().clear();

    if (rules == null) {
      return;
    }
    
    for (StdArrangementMatchRule rule : rules) {
      getModel().addElement(rule);
    }

    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info("Arrangement matching rules list is refreshed. Given rules:");
      for (StdArrangementMatchRule rule : rules) {
        LOG.info("  " + rule.toString());
      }
    }
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    int id = e.getID();
    switch (id) {
      case MouseEvent.MOUSE_PRESSED:
        onMousePressed(e);
        if (e.isConsumed()) {
          mySkipMouseClick = true;
          return;
        }
        break;
      case MouseEvent.MOUSE_CLICKED:
        if (mySkipMouseClick) {
          mySkipMouseClick = false;
          return;
        }
    }
    super.processMouseEvent(e);
  }

  private void onMouseMoved(@NotNull MouseEvent e) {
    int i = locationToIndex(e.getPoint());
    if (i != myRowUnderMouse) {
      onMouseExited();
    }
    
    if (i < 0) {
      return;
    }

    if (i != myRowUnderMouse) {
      onMouseEntered(e);
    }

    ArrangementListRowDecorator decorator = myComponents.get(i);
    if (decorator == null) {
      return;
    }

    Rectangle rectangle = decorator.onMouseMove(e);
    if (rectangle != null) {
      repaintScreenBounds(rectangle);
    }
  }

  private void repaintScreenBounds(@NotNull Rectangle bounds) {
    Point location = bounds.getLocation();
    SwingUtilities.convertPointFromScreen(location, this);
    int x = location.x;
    int width = bounds.width;
    repaint(x, location.y, width, bounds.height);
  }
  
  
  private void onMousePressed(@NotNull MouseEvent e) {
    int i = locationToIndex(e.getPoint());
    if (i < 0) {
      return;
    }
    ArrangementListRowDecorator decorator = myComponents.get(i);
    if (decorator != null) {
      decorator.onMousePress(e);
    }
  }
  
  private void onMouseExited() {
    if (myRowUnderMouse < 0) {
      return;
    }

    ArrangementListRowDecorator decorator = myComponents.get(myRowUnderMouse);
    if (decorator != null) {
      decorator.onMouseExited();
      repaintRows(myRowUnderMouse, myRowUnderMouse, false);
    }
    myRowUnderMouse = -1;
  }

  private void onMouseEntered(@NotNull MouseEvent e) {
    myRowUnderMouse = locationToIndex(e.getPoint());
    ArrangementListRowDecorator decorator = myComponents.get(myRowUnderMouse);
    if (decorator != null) {
      decorator.onMouseEntered(e);
      repaintRows(myRowUnderMouse, myRowUnderMouse, false);
    }
  }

  private void onSelectionChange(@NotNull ListSelectionEvent e) {
    ListSelectionModel model = getSelectionModel();
    for (int i = e.getFirstIndex(); i <= e.getLastIndex(); i++) {
      ArrangementListRowDecorator decorator = myComponents.get(i);
      if (decorator != null) {
        decorator.setSelected(model.isSelectedIndex(i));
      }
    }
  }

  @NotNull
  public List<StdArrangementMatchRule> getRules() {
    if (getModel().isEmpty()) {
      return Collections.emptyList();
    }
    List<StdArrangementMatchRule> result = new ArrayList<StdArrangementMatchRule>();
    for (int i = 0; i < getModel().size(); i++) {
      Object element = getModel().get(i);
      if (element instanceof StdArrangementMatchRule) {
        result.add((StdArrangementMatchRule)element);
      }
    }
    return result;
  }
  
  public void repaintRows(int first, int last, boolean rowStructureChanged) {
    Rectangle bounds = getCellBounds(first, last);
    if (rowStructureChanged) {
      for (int i = first; i <= last; i++) {
        myComponents.remove(i);
      }
    }
    if (bounds != null) {
      repaint(bounds);
    }
  }

  private class MyListCellRenderer implements ListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      ArrangementListRowDecorator component = myComponents.get(index);
      if (component == null) {
        if (!(value instanceof StdArrangementMatchRule)) {
          return EMPTY_RENDERER;
        }
        StdArrangementMatchRule rule = (StdArrangementMatchRule)value;
        component = new ArrangementListRowDecorator(myFactory.getComponent(rule.getMatcher().getCondition(), rule, true));
        myComponents.put(index, component);
        if (myRowUnderMouse == index) {
          component.setBackground(UIUtil.getDecoratedRowColor());
        }
        else {
          component.setBackground(UIUtil.getListBackground());
        }
      }
      component.setRowIndex(index + 1);
      return component.getUiComponent();
    }
  }
}
