package com.miwi.gitworktree.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.RowFilter;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.ListSelectionModel;
import javax.swing.BorderFactory;
import javax.swing.AbstractAction;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class WorktreeSettingsDialog extends DialogWrapper {
    private final Project project;
    private final JComboBox<LanguageCatalog.Option> languageBox = new JComboBox<>();
    private final JLabel languageLabel = new JLabel();
    private final JButton editTranslationsButton = new JButton();
    private final JButton editLanguageListButton = new JButton();
    private boolean reloadingLanguageOptions;
    private final String originalLanguage;

    public WorktreeSettingsDialog(Project project) {
        super(project);
        this.project = project;
        this.originalLanguage = I18n.language();
        setTitle(I18n.text("settings.title"));
        reloadLanguageOptions();
        languageBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : I18n.languageOptionLabel(value));
            if (isSelected) {
                label.setOpaque(true);
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });
        languageBox.addActionListener(event -> {
            LanguageCatalog.Option selected = (LanguageCatalog.Option) languageBox.getSelectedItem();
            if (!reloadingLanguageOptions && selected != null) {
                I18n.setLanguage(selected.id());
                updateLabels();
            }
        });
        init();
    }

    @Override protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel languageRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        languageRow.add(languageLabel);
        languageRow.add(languageBox);
        editTranslationsButton.addActionListener(event -> editTranslations());
        editLanguageListButton.addActionListener(event -> editLanguageList());
        languageRow.add(editTranslationsButton);
        languageRow.add(editLanguageListButton);
        panel.add(languageRow, BorderLayout.NORTH);
        updateLabels();
        return panel;
    }

    private void reloadLanguageOptions() {
        String selected = I18n.language();
        reloadingLanguageOptions = true;
        try {
            languageBox.removeAllItems();
            I18n.languageOptions().forEach(languageBox::addItem);
            for (int i = 0; i < languageBox.getItemCount(); i++) {
                if (languageBox.getItemAt(i).id().equals(selected)) languageBox.setSelectedIndex(i);
            }
        } finally {
            reloadingLanguageOptions = false;
        }
    }

    private void updateLabels() {
        setTitle(I18n.text("settings.title"));
        languageLabel.setText(I18n.text("settings.language"));
        languageBox.setToolTipText(I18n.text("settings.languageHint"));
        editTranslationsButton.setText(I18n.text("settings.editLanguage"));
        editLanguageListButton.setText(I18n.text("settings.manageLanguages"));
        setOKButtonText(I18n.text("button.save"));
        setCancelButtonText(I18n.text("button.cancel"));
        languageBox.repaint();
    }

    @Override public void doCancelAction() {
        I18n.setLanguage(originalLanguage);
        super.doCancelAction();
    }

    private void editTranslations() {
        TranslationEditor editor = new TranslationEditor(project);
        if (editor.show() == 0) {
            try {
                reloadLanguageOptions();
                updateLabels();
            } catch (Exception exception) {
                showImportError(exception);
            }
        }
    }

    private void editLanguageList() {
        LanguageListEditor editor = new LanguageListEditor(project);
        editor.show();
        reloadLanguageOptions();
        updateLabels();
    }

    private void showImportError(Exception exception) {
        Messages.showErrorDialog(project,
                I18n.format("settings.importInvalid", exception.getMessage()),
                I18n.text("settings.title"));
    }

    private static void copyToClipboard(Project project, String content) {
        CopyPasteManager.getInstance().setContents(new StringSelection(content));
        Messages.showInfoMessage(project, I18n.text("settings.exportCopied"), I18n.text("settings.title"));
    }

    private static String showTextInput(Project project, String title, String hint, String initialText) {
        JTextArea editor = new JTextArea(initialText, 20, 72);
        editor.setLineWrap(false);
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.add(new JLabel(hint), BorderLayout.NORTH);
        panel.add(new JScrollPane(editor), BorderLayout.CENTER);
        DialogBuilder builder = new DialogBuilder(project).title(title).centerPanel(panel);
        builder.addOkAction().setText(I18n.text("settings.import"));
        builder.addCancelAction().setText(I18n.text("button.cancel"));
        return builder.show() == 0 ? editor.getText() : null;
    }

    private final class TranslationEditor {
        private final Project project;
        private final JTabbedPane tabs = new JTabbedPane();
        private final LinkedHashMap<String, TranslationTableModel> models = new LinkedHashMap<>();
        private final LinkedHashMap<String, JTable> tables = new LinkedHashMap<>();
        private final DialogBuilder builder;
        private boolean handlingPlusTab;
        private boolean rebuildingTabs;
        private boolean hasChanges;
        private final Map<String, String> initialLanguageFiles;
        private final String initialSelectedLanguage;

        private TranslationEditor(Project project) {
            this.project = project;
            try {
                initialLanguageFiles = I18n.snapshotLanguageFiles();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not capture language editor state.", exception);
            }
            initialSelectedLanguage = I18n.language();
            reloadTabs(I18n.editableLanguageId());
            ChangeListener listener = event -> {
                handleTabSelection();
            };
            tabs.addChangeListener(listener);
            tabs.addMouseListener(new MouseAdapter() {
                private void handleTabMouse(MouseEvent event) {
                    int index = tabs.indexAtLocation(event.getX(), event.getY());
                    if (index < 0 || index == tabs.getTabCount() - 1) return;
                    String id = languageIdAt(index);
                    if (id == null) return;
                    if (event.isPopupTrigger()) {
                        tabs.setSelectedIndex(index);
                        showLanguageTabMenu(tabs, id, event.getX(), event.getY());
                    } else if (event.getID() == MouseEvent.MOUSE_CLICKED && event.getButton() == MouseEvent.BUTTON1
                            && event.getClickCount() == 2) {
                        tabs.setSelectedIndex(index);
                        beginTabRename(id);
                    }
                }
                @Override public void mousePressed(MouseEvent event) { handleTabMouse(event); }
                @Override public void mouseReleased(MouseEvent event) { handleTabMouse(event); }
                @Override public void mouseClicked(MouseEvent event) { handleTabMouse(event); }
            });
            JPanel panel = new JPanel(new BorderLayout());
            panel.add(tabs, BorderLayout.CENTER);
            builder = new DialogBuilder(project).title(I18n.text("settings.editLanguage")).centerPanel(panel);
            builder.addOkAction().setText(I18n.text("button.save"));
            builder.addCancelAction().setText(I18n.text("button.cancel"));
            builder.setOkOperation(() -> {
                try {
                    save();
                    builder.getDialogWrapper().close(0);
                } catch (Exception exception) {
                    showImportError(exception);
                }
            });
            builder.setCancelOperation(this::confirmCancel);
        }

        private int show() { return builder.show(); }

        private void reloadTabs(String selectedId) {
            rebuildingTabs = true;
            handlingPlusTab = true;
            models.keySet().retainAll(I18n.languageNames().keySet());
            tables.keySet().retainAll(I18n.languageNames().keySet());
            tabs.removeAll();
            for (Map.Entry<String, String> language : I18n.languageNames().entrySet()) {
                TranslationTableModel model = models.computeIfAbsent(language.getKey(),
                        id -> new TranslationTableModel(id, () -> hasChanges = true));
                tabs.addTab(language.getValue(), createTranslationPanel(model));
            }
            tabs.addTab("+", new JPanel());
            if (selectedId != null) {
                int index = indexOfLanguage(selectedId);
                if (index >= 0) tabs.setSelectedIndex(index);
            }
            int plusIndex = tabs.getTabCount() - 1;
            tabs.setToolTipTextAt(plusIndex, I18n.text("settings.addLanguage"));
            rebuildingTabs = false;
            handlingPlusTab = false;
        }

        private JPanel createTranslationPanel(TranslationTableModel model) {
            JPanel panel = new JPanel(new BorderLayout(6, 6));
            JTable table = new JTable(model);
            tables.put(model.languageId, table);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            TableRowSorter<TranslationTableModel> sorter = new TableRowSorter<>(model);
            sorter.setSortsOnUpdates(true);
            table.setRowSorter(sorter);
            table.getColumnModel().getColumn(0).setPreferredWidth(260);
            table.getColumnModel().getColumn(1).setPreferredWidth(560);
            table.setFillsViewportHeight(true);
            JScrollPane scrollPane = new JScrollPane(table);
            JTextField searchField = new JTextField(18);
            JCheckBox includeKey = new JCheckBox(I18n.text("settings.searchIncludeKey"));
            JLabel matchCount = new JLabel();
            JButton previous = new JButton("↑");
            JButton next = new JButton("↓");
            JButton close = new JButton(AllIcons.Actions.Close);
            close.setBorderPainted(false);
            close.setContentAreaFilled(false);
            close.setFocusable(false);
            previous.setToolTipText(I18n.text("settings.searchPrevious"));
            next.setToolTipText(I18n.text("settings.searchNext"));
            close.setToolTipText(I18n.text("settings.searchClose"));
            searchField.setToolTipText(I18n.text("settings.searchHint"));
            searchField.getAccessibleContext().setAccessibleName(I18n.text("settings.searchHint"));
            JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
            searchBar.setOpaque(true);
            searchBar.setBackground(UIManager.getColor("Panel.background"));
            Color borderColor = UIManager.getColor("Component.borderColor");
            if (borderColor == null) borderColor = UIManager.getColor("Separator.foreground");
            if (borderColor == null) borderColor = Color.GRAY;
            Border outline = BorderFactory.createLineBorder(borderColor);
            searchBar.setBorder(BorderFactory.createCompoundBorder(outline, BorderFactory.createEmptyBorder(2, 4, 2, 4)));
            searchBar.setVisible(false);

            JPanel searchControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            searchControls.add(searchField);
            searchControls.add(includeKey);
            searchControls.add(matchCount);
            searchControls.add(previous);
            searchControls.add(next);
            searchBar.setLayout(new BorderLayout(4, 0));
            searchBar.add(searchControls, BorderLayout.CENTER);
            searchBar.add(close, BorderLayout.EAST);

            Runnable updateMatchCount = () -> {
                String query = searchField.getText().trim();
                int total = table.getRowCount();
                if (query.isEmpty()) {
                    matchCount.setText("");
                    return;
                }
                int selected = table.getSelectedRow();
                matchCount.setText(I18n.format("settings.searchMatchCount", selected < 0 ? 0 : selected + 1, total));
            };
            Runnable applySearch = () -> {
                String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
                if (query.isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    boolean searchKeys = includeKey.isSelected();
                    sorter.setRowFilter(new RowFilter<>() {
                        @Override public boolean include(Entry<? extends TranslationTableModel, ? extends Integer> entry) {
                            String value = String.valueOf(entry.getValue(1)).toLowerCase(Locale.ROOT);
                            if (value.contains(query)) return true;
                            return searchKeys && String.valueOf(entry.getValue(0)).toLowerCase(Locale.ROOT).contains(query);
                        }
                    });
                }
                int matches = table.getRowCount();
                table.clearSelection();
                if (matches > 0) table.setRowSelectionInterval(0, 0);
                updateMatchCount.run();
            };
            Runnable showSearch = () -> {
                searchBar.setVisible(true);
                panel.revalidate();
                panel.repaint();
                searchField.requestFocusInWindow();
                searchField.selectAll();
            };
            Runnable hideSearch = () -> {
                searchField.setText("");
                searchBar.setVisible(false);
                panel.revalidate();
                panel.repaint();
                table.requestFocusInWindow();
            };
            java.util.function.IntConsumer navigateMatch = direction -> {
                int matches = table.getRowCount();
                if (matches == 0) return;
                int selected = table.getSelectedRow();
                int destination = selected < 0 ? (direction > 0 ? 0 : matches - 1)
                        : Math.floorMod(selected + direction, matches);
                table.setRowSelectionInterval(destination, destination);
                table.scrollRectToVisible(table.getCellRect(destination, 0, true));
                updateMatchCount.run();
            };
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { applySearch.run(); }
                @Override public void removeUpdate(DocumentEvent event) { applySearch.run(); }
                @Override public void changedUpdate(DocumentEvent event) { applySearch.run(); }
            });
            includeKey.addActionListener(event -> applySearch.run());
            table.getSelectionModel().addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting()) updateMatchCount.run();
            });
            previous.addActionListener(event -> navigateMatch.accept(-1));
            next.addActionListener(event -> navigateMatch.accept(1));
            close.addActionListener(event -> hideSearch.run());
            searchField.addActionListener(event -> navigateMatch.accept(1));
            searchField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeTranslationSearch");
            searchField.getActionMap().put("closeTranslationSearch", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { hideSearch.run(); }
            });

            JButton search = new JButton(I18n.text("settings.search"));
            search.addActionListener(event -> showSearch.run());
            panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "openTranslationSearch");
            panel.getActionMap().put("openTranslationSearch", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { showSearch.run(); }
            });
            panel.add(searchBar, BorderLayout.NORTH);
            panel.add(scrollPane, BorderLayout.CENTER);
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            JButton export = new JButton(I18n.text("settings.export"));
            export.addActionListener(event -> copyToClipboard(project, model.exportText()));
            JButton importButton = new JButton(I18n.text("settings.import"));
            importButton.addActionListener(event -> importMessages(model));
            controls.add(export);
            controls.add(importButton);
            controls.add(search);
            panel.add(controls, BorderLayout.SOUTH);
            return panel;
        }

        private void importMessages(TranslationTableModel model) {
            String text = showTextInput(project, I18n.text("settings.importLanguageTitle"),
                    I18n.text("settings.importLanguageHint"), model.exportText());
            if (text == null) return;
            try {
                I18n.importLanguageMessages(model.languageId, text);
                model.reload();
                hasChanges = true;
            } catch (Exception exception) {
                showImportError(exception);
            }
        }

        private void handleTabSelection() {
            if (handlingPlusTab || rebuildingTabs) return;
            if (tabs.getSelectedIndex() != tabs.getTabCount() - 1) {
                return;
            }
            handlingPlusTab = true;
            try {
                String id = I18n.addLanguage();
                models.put(id, new TranslationTableModel(id, () -> hasChanges = true));
                hasChanges = true;
                reloadTabs(id);
                int newIndex = indexOfLanguage(id);
                if (newIndex >= 0) {
                    tabs.setSelectedIndex(newIndex);
                    java.awt.Rectangle bounds = tabs.getBoundsAt(newIndex);
                    if (bounds != null) tabs.scrollRectToVisible(bounds);
                }
            } catch (Exception exception) {
                showImportError(exception);
                tabs.setSelectedIndex(Math.max(0, tabs.getTabCount() - 2));
            } finally {
                handlingPlusTab = false;
            }
        }

        private int indexOfLanguage(String id) {
            int index = 0;
            for (String languageId : I18n.languageNames().keySet()) {
                if (languageId.equals(id)) return index;
                index++;
            }
            return -1;
        }

        private String languageIdAt(int index) {
            if (index < 0 || index >= I18n.languageNames().size()) return null;
            return new ArrayList<>(I18n.languageNames().keySet()).get(index);
        }

        private void showLanguageTabMenu(java.awt.Component owner, String id, int x, int y) {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem delete = new JMenuItem(I18n.text("settings.deleteLanguage"));
            delete.setEnabled(I18n.canDeleteLanguage(id));
            delete.addActionListener(event -> deleteLanguage(id));
            menu.add(delete);
            menu.show(owner, x, y);
        }

        private void deleteLanguage(String id) {
            int selectedIndex = indexOfLanguage(id);
            List<String> ids = new ArrayList<>(I18n.languageNames().keySet());
            if (selectedIndex < 0 || !I18n.canDeleteLanguage(id)) return;
            String name = I18n.languageName(id);
            int confirmation = Messages.showYesNoDialog(project,
                    I18n.format("confirm.deleteLanguage", name), I18n.text("title.confirm"),
                    Messages.getQuestionIcon());
            if (confirmation != 0) return;
            String nextId = ids.get(selectedIndex == ids.size() - 1 ? selectedIndex - 1 : selectedIndex + 1);
            try {
                I18n.deleteLanguage(id);
                hasChanges = true;
                reloadTabs(nextId);
            } catch (Exception exception) {
                showImportError(exception);
            }
        }

        private void beginTabRename(String id) {
            int tabIndex = indexOfLanguage(id);
            if (tabIndex < 0) return;
            JTextField name = new JTextField(I18n.languageName(id), Math.max(8, I18n.languageName(id).length()));
            final boolean[] saving = {false};
            Runnable save = () -> {
                if (saving[0]) return;
                saving[0] = true;
                try {
                    I18n.renameLanguage(id, name.getText());
                    tabs.setTitleAt(tabIndex, I18n.languageName(id));
                    tabs.setTabComponentAt(tabIndex, null);
                    hasChanges = true;
                } catch (Exception exception) {
                    saving[0] = false;
                    showImportError(exception);
                    name.requestFocusInWindow();
                    return;
                }
                saving[0] = false;
            };
            name.addActionListener(event -> save.run());
            name.addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent event) { save.run(); }
            });
            tabs.setTabComponentAt(tabIndex, name);
            name.selectAll();
            name.requestFocusInWindow();
        }

        private void save() throws IOException {
            finishTableEditing();
            for (TranslationTableModel model : models.values()) {
                I18n.saveLanguageMessages(model.languageId, model.values());
            }
            hasChanges = false;
        }

        private void confirmCancel() {
            finishTableEditing();
            if (!hasChanges) {
                builder.getDialogWrapper().close(1);
                return;
            }
            int answer = Messages.showDialog(project, I18n.text("settings.unsavedLanguageChanges"),
                    I18n.text("title.confirm"), new String[]{I18n.text("button.save"),
                            I18n.text("settings.discardChanges"), I18n.text("button.cancel")},
                    2, Messages.getQuestionIcon());
            if (answer == 0) {
                try {
                    save();
                    builder.getDialogWrapper().close(0);
                } catch (Exception exception) {
                    showImportError(exception);
                }
            } else if (answer == 1) {
                try {
                    I18n.restoreLanguageFiles(initialLanguageFiles, initialSelectedLanguage);
                    builder.getDialogWrapper().close(1);
                } catch (Exception exception) {
                    showImportError(exception);
                }
            }
        }

        private void finishTableEditing() {
            tables.values().forEach(table -> {
                if (table.isEditing()) table.getCellEditor().stopCellEditing();
            });
        }
    }

    private static final class TranslationTableModel extends AbstractTableModel {
        private final String languageId;
        private final Runnable onChange;
        private final List<String> keys = new ArrayList<>();
        private final LinkedHashMap<String, String> messages = new LinkedHashMap<>();

        private TranslationTableModel(String languageId, Runnable onChange) {
            this.languageId = languageId;
            this.onChange = onChange;
            reload();
        }

        private void reload() {
            Map<String, String> imported = LanguageCatalog.parseKeyValues(I18n.exportLanguageMessages(languageId));
            messages.clear();
            messages.putAll(new TreeMap<>(imported));
            keys.clear();
            keys.addAll(messages.keySet());
            fireTableDataChanged();
        }

        private Map<String, String> values() { return new LinkedHashMap<>(messages); }

        private String exportText() { return LanguageCatalog.exportKeyValuesForUi(messages); }

        @Override public int getRowCount() { return keys.size(); }
        @Override public int getColumnCount() { return 2; }
        @Override public String getColumnName(int column) {
            return I18n.text(column == 0 ? "settings.key" : "settings.value");
        }
        @Override public Object getValueAt(int row, int column) {
            String key = keys.get(row);
            return column == 0 ? key : messages.get(key);
        }
        @Override public boolean isCellEditable(int row, int column) { return column == 1; }
        @Override public void setValueAt(Object value, int row, int column) {
            if (column != 1) return;
            messages.put(keys.get(row), value == null ? "" : value.toString());
            fireTableCellUpdated(row, column);
            onChange.run();
        }
    }

    private final class LanguageListEditor {
        private final Project project;
        private final LanguageNamesTableModel model = new LanguageNamesTableModel();
        private final JTable table = new JTable(model);

        private LanguageListEditor(Project project) { this.project = project; }

        private void show() {
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setFillsViewportHeight(true);
            table.addMouseListener(new MouseAdapter() {
                private void showDeleteMenu(MouseEvent event) {
                    if (!event.isPopupTrigger()) return;
                    int row = table.rowAtPoint(event.getPoint());
                    if (row < 0) return;
                    table.setRowSelectionInterval(row, row);
                    String id = (String) model.getValueAt(row, 0);
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem delete = new JMenuItem(I18n.text("settings.deleteLanguage"));
                    delete.setEnabled(I18n.canDeleteLanguage(id));
                    delete.addActionListener(action -> deleteLanguageRow(id));
                    menu.add(delete);
                    menu.show(table, event.getX(), event.getY());
                }
                @Override public void mouseClicked(MouseEvent event) {
                    if (event.getButton() == MouseEvent.BUTTON3 && !event.isPopupTrigger()) showDeleteMenu(event);
                }
                @Override public void mousePressed(MouseEvent event) { showDeleteMenu(event); }
                @Override public void mouseReleased(MouseEvent event) { showDeleteMenu(event); }
            });
            table.getColumnModel().getColumn(0).setPreferredWidth(180);
            table.getColumnModel().getColumn(1).setPreferredWidth(360);
            JPanel panel = new JPanel(new BorderLayout(6, 6));
            panel.add(new JScrollPane(table), BorderLayout.CENTER);
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            JButton add = new JButton(I18n.text("settings.addLanguage"));
            add.addActionListener(event -> addLanguage());
            JButton export = new JButton(I18n.text("settings.export"));
            export.addActionListener(event -> copyToClipboard(project, I18n.exportLanguageNames()));
            JButton importButton = new JButton(I18n.text("settings.import"));
            importButton.addActionListener(event -> importNames());
            controls.add(add);
            controls.add(export);
            controls.add(importButton);
            panel.add(controls, BorderLayout.SOUTH);
            DialogBuilder builder = new DialogBuilder(project).title(I18n.text("settings.manageLanguages")).centerPanel(panel);
            builder.addOkAction().setText(I18n.text("button.close"));
            builder.show();
        }

        private void deleteLanguageRow(String id) {
            if (!I18n.canDeleteLanguage(id)) return;
            int confirmation = Messages.showYesNoDialog(project,
                    I18n.format("confirm.deleteLanguage", I18n.languageName(id)), I18n.text("title.confirm"),
                    Messages.getQuestionIcon());
            if (confirmation != 0) return;
            try {
                I18n.deleteLanguage(id);
                model.reload();
            } catch (Exception exception) {
                showImportError(exception);
            }
        }

        private void addLanguage() {
            try {
                String id = I18n.addLanguage();
                model.reload();
                int row = model.rowForId(id);
                if (row >= 0) {
                    table.setRowSelectionInterval(row, row);
                    table.scrollRectToVisible(table.getCellRect(row, 1, true));
                }
            } catch (Exception exception) {
                showImportError(exception);
            }
        }

        private void importNames() {
            String text = showTextInput(project, I18n.text("settings.importLanguageListTitle"),
                    I18n.text("settings.importLanguageListHint"), I18n.exportLanguageNames());
            if (text == null) return;
            try {
                I18n.importLanguageNames(text);
                model.reload();
            } catch (Exception exception) {
                showImportError(exception);
            }
        }
    }

    private static final class LanguageNamesTableModel extends AbstractTableModel {
        private final List<String> ids = new ArrayList<>();
        private final LinkedHashMap<String, String> names = new LinkedHashMap<>();

        private LanguageNamesTableModel() { reload(); }

        private void reload() {
            ids.clear();
            names.clear();
            I18n.languageNames().forEach((id, name) -> {
                ids.add(id);
                names.put(id, name);
            });
            fireTableDataChanged();
        }

        private int rowForId(String id) { return ids.indexOf(id); }

        @Override public int getRowCount() { return ids.size(); }
        @Override public int getColumnCount() { return 2; }
        @Override public String getColumnName(int column) {
            return I18n.text(column == 0 ? "settings.languageId" : "settings.languageName");
        }
        @Override public Object getValueAt(int row, int column) { return column == 0 ? ids.get(row) : names.get(ids.get(row)); }
        @Override public boolean isCellEditable(int row, int column) { return column == 1; }
        @Override public void setValueAt(Object value, int row, int column) {
            if (column != 1) return;
            String id = ids.get(row);
            try {
                I18n.renameLanguage(id, value == null ? "" : value.toString());
                names.put(id, I18n.languageName(id));
                fireTableCellUpdated(row, column);
            } catch (Exception exception) {
                Messages.showErrorDialog(I18n.format("settings.importInvalid", exception.getMessage()), I18n.text("settings.manageLanguages"));
                fireTableCellUpdated(row, column);
            }
        }
    }
}
