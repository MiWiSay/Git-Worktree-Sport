package com.miwi.gitworktree.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.miwi.gitworktree.model.WorktreeEntry;
import com.miwi.gitworktree.service.ModalOperation;
import com.miwi.gitworktree.service.WorktreeService;
import com.miwi.gitworktree.service.WorktreeRemovalPolicy;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorktreesPanel extends JPanel implements Disposable {
    private final Project project;
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode(I18n.text("tree.repositories"));
    private final JTree tree = new JTree(new DefaultTreeModel(root));
    private final WorktreeHighlight highlight = new WorktreeHighlight(tree);
    private Runnable pendingButtonAction;
    private final JButton refreshButton = new JButton(I18n.text("action.refresh"));
    private final JButton settingsButton = new JButton(AllIcons.General.Gear);
    private final javax.swing.Timer actionButtonClickTimer = new javax.swing.Timer(350, event -> {
        Runnable action = pendingButtonAction;
        pendingButtonAction = null;
        if (action != null) action.run();
    });
    private final Runnable languageListener = () -> SwingUtilities.invokeLater(this::updateLanguage);
    private List<WorktreeEntry> currentEntries = List.of();
    private long lastPopupWhen;
    private int lastPopupX;
    private int lastPopupY;
    private long refreshVersion;
    private volatile boolean disposed;

    public WorktreesPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        root.add(new DefaultMutableTreeNode(new RootLabel(I18n.text("tree.loading"))));
        ((DefaultTreeModel) tree.getModel()).reload();
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new WorktreeRenderer());
        tree.setRowHeight(JBUI.scale(29));
        refreshButton.setToolTipText(I18n.text("action.refresh"));
        refreshButton.addActionListener(event -> refresh());
        settingsButton.setToolTipText(I18n.text("settings.title"));
        settingsButton.setBorderPainted(false);
        settingsButton.setContentAreaFilled(false);
        settingsButton.setFocusPainted(false);
        settingsButton.setMargin(JBUI.emptyInsets());
        settingsButton.setBorder(JBUI.Borders.empty());
        settingsButton.setPreferredSize(JBUI.size(28, 28));
        settingsButton.setMinimumSize(JBUI.size(28, 28));
        settingsButton.setMaximumSize(JBUI.size(28, 28));
        settingsButton.addActionListener(event -> openSettings());

        JPanel toolbarActions = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
        toolbarActions.add(refreshButton);
        toolbarActions.add(settingsButton);
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBorder(JBUI.Borders.empty(4, 6));
        toolbar.add(toolbarActions, BorderLayout.WEST);
        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);

        tree.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { maybeShowPopup(event); }
            @Override public void mouseReleased(MouseEvent event) { maybeShowPopup(event); }
            @Override public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                if (event.getClickCount() == 2) {
                    actionButtonClickTimer.stop();
                    pendingButtonAction = null;
                } else if (event.getClickCount() == 1) {
                    scheduleActionButton(event);
                }
            }
        });
        I18n.addLanguageListener(languageListener);
        refresh();
    }

    private void openSettings() {
        new WorktreeSettingsDialog(project).showAndGet();
    }

    public void refresh() {
        if (disposed || project.isDisposed()) return;
        long version = ++refreshVersion;
        refreshButton.setEnabled(false);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                List<WorktreeEntry> entries = WorktreeService.list(project);
                ApplicationManager.getApplication().invokeLater(() -> {
                    // 首次显示与创建完成可能同时刷新，迟到的旧结果不能覆盖新列表。
                    if (!disposed && !project.isDisposed() && version == refreshVersion) rebuild(entries);
                });
            } catch (Exception e) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (disposed || project.isDisposed() || version != refreshVersion) return;
                    refreshButton.setEnabled(true);
                    Messages.showErrorDialog(project, message(e), I18n.text("title.manager"));
                });
            }
        });
    }

    public void refreshAndReveal(Path path) {
        if (disposed || project.isDisposed()) return;
        highlight.request(path);
        refresh();
    }

    @Override public void dispose() {
        disposed = true;
        actionButtonClickTimer.stop();
        pendingButtonAction = null;
        I18n.removeLanguageListener(languageListener);
        if (SwingUtilities.isEventDispatchThread()) highlight.close();
        else SwingUtilities.invokeLater(highlight::close);
    }

    private void rebuild(List<WorktreeEntry> entries) {
        currentEntries = List.copyOf(entries);
        root.removeAllChildren();
        Map<String, List<WorktreeEntry>> grouped = new LinkedHashMap<>();
        entries.stream().sorted(Comparator.comparing(WorktreeEntry::repositoryRoot, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(WorktreeEntry::path, String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> grouped.computeIfAbsent(entry.repositoryRoot(), ignored -> new ArrayList<>()).add(entry));

        for (Map.Entry<String, List<WorktreeEntry>> group : grouped.entrySet()) {
            List<WorktreeEntry> worktrees = group.getValue();
            String projectName = new File(group.getKey()).getName();
            DefaultMutableTreeNode repositoryNode = new DefaultMutableTreeNode(new RepositoryLabel(projectName, worktrees.size()));
            root.add(repositoryNode);
            for (WorktreeGroups.Group location : WorktreeGroups.group(worktrees)) {
                List<WorktreeEntry> sameName = location.entries();
                // 只有重名条目增加分组层；唯一条目继续直接显示状态和操作菜单。
                DefaultMutableTreeNode parent = repositoryNode;
                if (sameName.size() > 1) {
                    parent = new DefaultMutableTreeNode(new WorktreeLocationLabel(location.name(), sameName.size()));
                    repositoryNode.add(parent);
                }
                for (WorktreeEntry entry : sameName) {
                    DefaultMutableTreeNode worktreeNode = new DefaultMutableTreeNode(entry);
                    worktreeNode.add(new DefaultMutableTreeNode(new OpenWorktreeButtons(entry)));
                    worktreeNode.add(detail("detail.type", classification(entry)));
                    worktreeNode.add(detail("detail.bare", yesNo(entry.bare())));
                    worktreeNode.add(detail("detail.locked", yesNo(entry.effectivelyLocked())));
                    worktreeNode.add(detail("detail.branch", displayBranch(entry.branch())));
                    worktreeNode.add(detail("detail.head", entry.head().isBlank() ? I18n.text("status.noCommit") : entry.head()));
                    worktreeNode.add(detail("detail.created", creationTime(entry.path())));
                    worktreeNode.add(new DefaultMutableTreeNode(new WorktreeDetail(I18n.text("detail.path"), entry.path(),
                            true, entry.prunable() || !entry.pathExists())));
                    parent.add(worktreeNode);
                }
            }
        }
        if (grouped.isEmpty()) root.add(new DefaultMutableTreeNode(new RootLabel(I18n.text("tree.noRepositories"))));

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        model.reload();
        for (int row = 0; row < tree.getRowCount(); row++) {
            Object item = ((DefaultMutableTreeNode) tree.getPathForRow(row).getLastPathComponent()).getUserObject();
            if (item instanceof RepositoryLabel) tree.expandRow(row);
        }
        refreshButton.setEnabled(true);
        highlight.afterRebuild(root);
    }

    private DefaultMutableTreeNode detail(String key, String value) {
        return new DefaultMutableTreeNode(new WorktreeDetail(I18n.text(key), value, false, false));
    }

    private void updateLanguage() {
        if (disposed || project.isDisposed()) return;
        refreshButton.setText(I18n.text("action.refresh"));
        refreshButton.setToolTipText(I18n.text("action.refresh"));
        settingsButton.setToolTipText(I18n.text("settings.title"));
        rebuild(currentEntries);
    }

    private void maybeShowPopup(MouseEvent event) {
        boolean rightClickRelease = SwingUtilities.isRightMouseButton(event) && event.getID() == MouseEvent.MOUSE_RELEASED;
        if (!event.isPopupTrigger() && !rightClickRelease) return;
        if (event.getWhen() - lastPopupWhen < 300 && event.getX() == lastPopupX && event.getY() == lastPopupY) return;
        lastPopupWhen = event.getWhen();
        lastPopupX = event.getX();
        lastPopupY = event.getY();

        TreePath path = tree.getPathForLocation(event.getX(), event.getY());
        if (path == null) {
            tree.clearSelection();
            BranchCreationPopup.show(project, event);
            return;
        }
        java.awt.Rectangle bounds = tree.getPathBounds(path);
        if (bounds == null || event.getY() < bounds.y || event.getY() >= bounds.y + bounds.height) return;
        tree.setSelectionPath(path);
        Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        JPopupMenu menu = new JPopupMenu();
        if (value instanceof WorktreeEntry entry) {
            if (!entry.protectedFromOpenWorktree()) add(menu, "action.openWorktree", () -> openWorktree(entry));
            add(menu, "action.open", () -> open(entry));
            if (!entry.protectedFromGitLockChanges()) {
                if (entry.locked()) add(menu, "action.unlock", () -> unlock(entry));
                else add(menu, "action.lock", () -> lock(entry));
            }
            menu.addSeparator();
            add(menu, "action.repair", () -> repair(entry));
            if (entry.cleanupDeleteAction() == WorktreeEntry.CleanupDeleteAction.PRUNE) {
                add(menu, "action.prune", () -> prune(entry));
            } else if (entry.cleanupDeleteAction() == WorktreeEntry.CleanupDeleteAction.DELETE) {
                add(menu, "action.move", () -> move(entry));
                add(menu, "action.delete", () -> delete(entry));
            }
        } else if (value instanceof RepositoryLabel) {
            add(menu, "action.refresh", this::refresh);
        } else if (value instanceof WorktreeLocationLabel) {
            // 分组不是具体工作树，不能把删除等动作隐式指向任意子项。
            add(menu, "action.refresh", this::refresh);
        } else if (value instanceof WorktreeDetail detail) {
            add(menu, "action.copyValue", () -> CopyPasteManager.getInstance()
                    .setContents(new java.awt.datatransfer.StringSelection(detail.value())));
        }
        if (menu.getComponentCount() > 0) menu.show(tree, event.getX(), event.getY());
    }

    private void add(JPopupMenu menu, String key, Runnable callback) {
        JMenuItem item = new JMenuItem(I18n.text(key));
        item.setMargin(new Insets(7, 12, 7, 12));
        item.setFont(item.getFont().deriveFont(item.getFont().getSize2D() + 1f));
        Dimension preferredSize = item.getPreferredSize();
        item.setPreferredSize(new Dimension(preferredSize.width, Math.max(36, preferredSize.height)));
        item.addActionListener(event -> callback.run());
        menu.add(item);
    }

    private void scheduleActionButton(MouseEvent event) {
        TreePath path = tree.getPathForLocation(event.getX(), event.getY());
        if (path == null) {
            actionButtonClickTimer.stop();
            pendingButtonAction = null;
            return;
        }
        Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (!(value instanceof OpenWorktreeButtons buttons)) {
            actionButtonClickTimer.stop();
            pendingButtonAction = null;
            return;
        }
        java.awt.Rectangle bounds = tree.getPathBounds(path);
        if (bounds == null) return;
        int localX = event.getX() - bounds.x;
        boolean hasOpenWorktree = !buttons.entry().protectedFromOpenWorktree();
        int openWidth = hasOpenWorktree ? buttonWidth(I18n.text("action.openWorktree")) : 0;
        int folderWidth = buttonWidth(I18n.text("detail.openFolderButton"));
        int firstStart = 4;
        int folderStart = hasOpenWorktree ? firstStart + openWidth + JBUI.scale(8) : firstStart;
        if (hasOpenWorktree && localX >= firstStart && localX <= firstStart + openWidth) {
            pendingButtonAction = () -> openWorktree(buttons.entry());
        } else if (localX >= folderStart && localX <= folderStart + folderWidth) {
            pendingButtonAction = () -> open(buttons.entry());
        } else {
            pendingButtonAction = null;
        }
        if (pendingButtonAction == null) actionButtonClickTimer.stop();
        else actionButtonClickTimer.restart();
    }

    private int buttonWidth(String label) {
        return actionButton(label, tree.getForeground()).getPreferredSize().width;
    }

    private JLabel actionButton(String key, Color foreground) {
        return new WorktreeActionLabel(I18n.text(key), foreground, tree.getFont());
    }

    private Object selectedValue() {
        TreePath selected = tree.getSelectionPath();
        return selected == null ? null : ((DefaultMutableTreeNode) selected.getLastPathComponent()).getUserObject();
    }

    public WorktreeEntry selectedWorktree() {
        Object selected = selectedValue();
        return selected instanceof WorktreeEntry entry ? entry : null;
    }

    public void performContextAction(String operation) {
        Object selected = selectedValue();
        if (!(selected instanceof WorktreeEntry entry)) {
            Messages.showWarningDialog(project, I18n.text("error.selectWorktree"), I18n.text("title.manager"));
            return;
        }
        if (entry.protectedFromGitLockChanges() && (operation.equals("lock") || operation.equals("unlock"))) {
            String key = entry.main()
                    ? (operation.equals("lock") ? "error.mainLockOperation" : "error.mainCannotUnlock")
                    : "error.currentWorktreeProtected";
            Messages.showWarningDialog(project, I18n.text(key), I18n.text(operation.equals("lock") ? "action.lock" : "action.unlock"));
            return;
        }
        if (entry.protectedFromPrune() && operation.equals("prune")) {
            String key = entry.main() ? "error.mainProtectedPrune" : "error.currentWorktreeProtected";
            Messages.showWarningDialog(project, I18n.text(key), I18n.text("action.prune"));
            return;
        }
        if (operation.equals("prune") && entry.cleanupDeleteAction() != WorktreeEntry.CleanupDeleteAction.PRUNE) {
            Messages.showWarningDialog(project, I18n.text("error.pruneHealthyWorktree"), I18n.text("action.prune"));
            return;
        }
        if (operation.equals("delete") && entry.cleanupDeleteAction() != WorktreeEntry.CleanupDeleteAction.DELETE) {
            Messages.showWarningDialog(project, I18n.text("error.deleteStaleWorktree"), I18n.text("action.delete"));
            return;
        }
        if (entry.protectedFromMoveOrDelete() && (operation.equals("move") || operation.equals("delete"))) {
            String key = entry.main()
                    ? (operation.equals("move") ? "confirm.moveMain" : "confirm.deleteMain")
                    : entry.currentProject() ? "error.currentWorktreeProtected" : "error.lockedProtected";
            Messages.showWarningDialog(project, I18n.text(key), I18n.text("title.manager"));
            return;
        }
        switch (operation) {
            case "openWorktree" -> openWorktree(entry);
            case "open" -> open(entry);
            case "lock" -> lock(entry);
            case "unlock" -> unlock(entry);
            case "repair" -> repair(entry);
            case "prune" -> prune(entry);
            case "move" -> move(entry);
            case "delete" -> delete(entry);
            default -> throw new IllegalArgumentException(I18n.format("error.unknownOperation", operation));
        }
    }

    private void open(WorktreeEntry entry) {
        try {
            if (!entry.pathExists()) throw new IllegalStateException(I18n.text("error.missingPath") + "\n" + entry.path());
            Desktop.getDesktop().open(new File(entry.path()));
        } catch (Exception e) {
            Messages.showErrorDialog(project, message(e), I18n.text("action.open"));
        }
    }

    private void openWorktree(WorktreeEntry entry) {
        try {
            if (!entry.pathExists()) throw new IllegalStateException(I18n.text("error.missingPath") + "\n" + entry.path());
            if (ProjectUtil.openOrImport(Path.of(entry.path()), project, false) == null) {
                throw new IllegalStateException(I18n.text("error.openProjectCancelled"));
            }
        } catch (ProcessCanceledException ignored) {
            // 取消 IDE 的打开/导入流程不会修改 Worktree。
        } catch (Exception e) {
            Messages.showErrorDialog(project, message(e), I18n.text("action.openWorktree"));
        }
    }

    private void repair(WorktreeEntry entry) {
        if (!confirm("confirm.repair", entry)) return;
        execute("action.repair", "progress.repair", () -> {
            WorktreeService.repair(entry);
            return null;
        });
    }

    private void prune(WorktreeEntry entry) {
        if (entry.protectedFromPrune()) {
            String key = entry.main() ? "error.mainProtectedPrune" : "error.currentWorktreeProtected";
            Messages.showWarningDialog(project, I18n.text(key), I18n.text("action.prune"));
            return;
        }
        if (entry.cleanupDeleteAction() != WorktreeEntry.CleanupDeleteAction.PRUNE) {
            Messages.showWarningDialog(project, I18n.text("error.pruneHealthyWorktree"), I18n.text("action.prune"));
            return;
        }
        try {
            String preview = WorktreeService.prunePreview(entry);
            String details = preview.isBlank() ? I18n.text("prune.none") : preview;
            if (!confirm("confirm.prune", entry.path() + "\n\n" + details)) return;
            ModalOperation.run(project, I18n.text("action.prune"), I18n.text("progress.prune"), () -> {
                WorktreeService.prune(entry);
                return null;
            });
            refresh();
            Messages.showInfoMessage(project, I18n.text("operation.success"), I18n.text("action.prune"));
        } catch (Exception e) {
            Messages.showErrorDialog(project, message(e), I18n.text("operation.failed"));
        }
    }

    private void lock(WorktreeEntry entry) {
        if (entry.protectedFromGitLockChanges()) {
            String key = entry.main() ? "status.mainLocked" : "error.currentWorktreeProtected";
            Messages.showInfoMessage(project, I18n.text(key), I18n.text("action.lock"));
            return;
        }
        if (!confirm("confirm.lock", entry)) return;
        execute("action.lock", "progress.lock", () -> { WorktreeService.lock(entry, I18n.text("lock.reason")); return null; });
    }

    private void unlock(WorktreeEntry entry) {
        if (entry.protectedFromGitLockChanges()) {
            String key = entry.main() ? "error.mainCannotUnlock" : "error.currentWorktreeProtected";
            Messages.showWarningDialog(project, I18n.text(key), I18n.text("action.unlock"));
            return;
        }
        if (!confirm("confirm.unlock", entry)) return;
        execute("action.unlock", "progress.unlock", () -> { WorktreeService.unlock(entry); return null; });
    }

    private void move(WorktreeEntry entry) {
        if (entry.protectedFromMoveOrDelete()) {
            String key = entry.main() ? "confirm.moveMain"
                    : entry.currentProject() ? "error.currentWorktreeProtected" : "error.lockedProtected";
            Messages.showWarningDialog(project, I18n.text(key), I18n.text("action.move"));
            return;
        }
        PathDialog dialog = new PathDialog(project, entry.path());
        if (!dialog.showAndGet()) return;
        Path destination;
        try { destination = Path.of(dialog.path()).toAbsolutePath().normalize(); }
        catch (Exception e) { Messages.showErrorDialog(project, message(e), I18n.text("action.move")); return; }
        execute("action.move", "progress.move", () -> { WorktreeService.move(project, entry, destination); return null; });
    }

    private void delete(WorktreeEntry entry) {
        if (entry.protectedFromMoveOrDelete()) {
            String key = entry.main() ? "confirm.deleteMain"
                    : entry.currentProject() ? "error.currentWorktreeProtected" : "error.lockedProtected";
            Messages.showWarningDialog(project, I18n.text(key), I18n.text("action.delete"));
            return;
        }
        if (entry.cleanupDeleteAction() != WorktreeEntry.CleanupDeleteAction.DELETE) {
            Messages.showWarningDialog(project, I18n.text("error.deleteStaleWorktree"), I18n.text("action.delete"));
            return;
        }
        if (!confirm("confirm.delete", entry)) return;
        executeDelete(entry);
    }

    private void executeDelete(WorktreeEntry entry) {
        try {
            ModalOperation.run(project, I18n.text("action.delete"), I18n.text("progress.delete"), () -> {
                WorktreeService.remove(project, entry);
                return null;
            });
            refresh();
            Messages.showInfoMessage(project, I18n.text("operation.success"), I18n.text("action.delete"));
        } catch (Exception failure) {
            if (!WorktreeRemovalPolicy.allowsForceRetry(failure)) {
                Messages.showErrorDialog(project, message(failure), I18n.text("operation.failed"));
                return;
            }
            if (!confirm("confirm.forceDelete", entry)) return;
            try {
                ModalOperation.run(project, I18n.text("action.delete"), I18n.text("progress.forceDelete"), () -> {
                    WorktreeService.forceRemove(project, entry);
                    return null;
                });
                refresh();
                Messages.showInfoMessage(project, I18n.text("operation.success"), I18n.text("action.delete"));
            } catch (Exception forceFailure) {
                Messages.showErrorDialog(project, message(forceFailure), I18n.text("operation.failed"));
            }
        }
    }

    private boolean confirm(String key, WorktreeEntry entry) {
        return confirm(key, entry.path());
    }

    private boolean confirm(String key, String details) {
        int answer = Messages.showDialog(project, I18n.text(key) + "\n\n" + details, I18n.text("title.confirm"),
                new String[]{I18n.text("button.continue"), I18n.text("button.cancel")}, 1, Messages.getWarningIcon());
        return answer == 0;
    }

    private void execute(String actionKey, String progressKey, CheckedOperation operation) {
        try {
            ModalOperation.run(project, I18n.text(actionKey), I18n.text(progressKey), operation::run);
            refresh();
            Messages.showInfoMessage(project, I18n.text("operation.success"), I18n.text(actionKey));
        } catch (Exception e) {
            Messages.showErrorDialog(project, message(e), I18n.text("operation.failed"));
        }
    }

    private static String message(Exception e) { return e.getMessage() == null ? e.toString() : e.getMessage(); }
    @FunctionalInterface private interface CheckedOperation { Object run() throws Exception; }

    private record RootLabel(String name) { @Override public String toString() { return name; } }
    private record RepositoryLabel(String name, int count) { @Override public String toString() { return name; } }
    private record WorktreeLocationLabel(String name, int count) {
        @Override public String toString() { return name; }
    }

    private record WorktreeDetail(String label, String value, boolean path, boolean missing) {
        @Override public String toString() { return label + ": " + value; }
    }

    private record OpenWorktreeButtons(WorktreeEntry entry) {}

    private static final class WorktreeActionLabel extends JLabel {
        private static final Color BACKGROUND = new JBColor(new Color(0xF1F2F4), new Color(0x242629));
        private static final Color OUTLINE = new JBColor(new Color(0xAEB4BD), new Color(0x41464E));

        private WorktreeActionLabel(String text, Color foreground, java.awt.Font font) {
            super(text);
            setFont(font);
            setForeground(foreground);
            setOpaque(false);
            int verticalPadding = JBUI.scale(4);
            int horizontalPadding = JBUI.scale(10);
            setBorder(BorderFactory.createEmptyBorder(verticalPadding, horizontalPadding,
                    verticalPadding, horizontalPadding));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = JBUI.scale(9);
            g.setColor(BACKGROUND);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.setColor(OUTLINE);
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static String creationTime(String path) {
        return WorktreeCreatedTime.format(Path.of(path), I18n.text("value.unavailable"));
    }

    private final class WorktreeRenderer extends JPanel implements TreeCellRenderer {
        private final JPanel content = new JPanel();

        private WorktreeRenderer() {
            setOpaque(true);
            setLayout(new BorderLayout());
            content.setOpaque(false);
            content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.X_AXIS));
            add(content, BorderLayout.CENTER);
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 4));
        }

        @Override public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                                           boolean expanded, boolean leaf, int row,
                                                                           boolean hasFocus) {
            content.removeAll();
            Color selection = UIManager.getColor("Tree.selectionBackground");
            Color foreground = UIManager.getColor("Tree.textForeground");
            Color selectionForeground = UIManager.getColor("Tree.selectionForeground");
            setBackground(selected && selection != null ? selection : tree.getBackground());
            if (selected && selectionForeground != null) foreground = selectionForeground;
            if (foreground == null) foreground = tree.getForeground();

            Object item = ((DefaultMutableTreeNode) value).getUserObject();
            if (item instanceof WorktreeEntry entry && highlight.isTarget(entry)) {
                // 熄灭阶段也覆盖普通选中色，否则选中的蓝色会掩盖闪烁效果。
                setBackground(highlight.isLit() ? new JBColor(new Color(53, 116, 240), new Color(47, 85, 145))
                        : tree.getBackground());
                if (highlight.isLit() && selectionForeground != null) foreground = selectionForeground;
            }
            if (item instanceof RepositoryLabel repository) {
                content.add(iconLabel(AllIcons.Vcs.Branch, foreground));
                content.add(textLabel(repository.name(), foreground, true));
                content.add(Box.createHorizontalStrut(7));
                content.add(new BadgeLabel(WorktreeGroups.countLabel(repository.count()), JBColor.GREEN, true));
            } else if (item instanceof WorktreeLocationLabel location) {
                content.add(iconLabel(AllIcons.Vcs.BranchNode, foreground));
                content.add(textLabel(location.name(), foreground, false));
                content.add(Box.createHorizontalStrut(JBUI.scale(7)));
                content.add(new BadgeLabel(WorktreeGroups.countLabel(location.count()), JBColor.GRAY, false));
            } else if (item instanceof WorktreeEntry entry) {
                content.add(iconLabel(AllIcons.Vcs.BranchNode, foreground));
                String status = statusKey(entry);
                content.add(new BadgeLabel(I18n.text(status), statusColor(entry), false));
                content.add(Box.createHorizontalStrut(8));
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                boolean grouped = node.getParent() instanceof DefaultMutableTreeNode parent
                        && parent.getUserObject() instanceof WorktreeLocationLabel;
                content.add(textLabel(WorktreeGroups.label(entry, grouped), foreground, false));
            } else if (item instanceof OpenWorktreeButtons buttons) {
                JPanel actions = new JPanel();
                actions.setOpaque(false);
                actions.setLayout(new javax.swing.BoxLayout(actions, javax.swing.BoxLayout.X_AXIS));
                if (!buttons.entry().protectedFromOpenWorktree()) {
                    actions.add(actionButton("action.openWorktree", foreground));
                    actions.add(Box.createHorizontalStrut(JBUI.scale(8)));
                }
                actions.add(actionButton("detail.openFolderButton", foreground));
                content.add(Box.createHorizontalStrut(2));
                content.add(actions);
            } else if (item instanceof WorktreeDetail detail) {
                content.add(textLabel(detail.label() + ": ", foreground, true));
                WavyLabel valueLabel = (WavyLabel) textLabel(detail.value(), foreground, false);
                if (detail.path()) valueLabel.setWave(detail.missing());
                content.add(valueLabel);
            } else if (item instanceof RootLabel label) {
                content.add(textLabel(label.name(), foreground, false));
            }
            return this;
        }

        private JLabel iconLabel(javax.swing.Icon icon, Color foreground) {
            JLabel label = new JLabel(icon);
            label.setForeground(foreground);
            label.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 5));
            return label;
        }

        private JLabel textLabel(String text, Color foreground, boolean bold) {
            WavyLabel label = new WavyLabel(text);
            label.setForeground(foreground);
            label.setFont(tree.getFont().deriveFont(bold ? java.awt.Font.BOLD : java.awt.Font.PLAIN));
            label.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));
            return label;
        }
    }

    private static final class BadgeLabel extends JLabel {
        private final Color accent;
        private final boolean countBadge;

        private BadgeLabel(String text, Color accent, boolean countBadge) {
            super(text);
            this.accent = accent;
            this.countBadge = countBadge;
            setForeground(accent);
            setFont(getFont().deriveFont(java.awt.Font.PLAIN, Math.max(11f, getFont().getSize2D() - 1f)));
            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color light = countBadge ? new Color(234, 245, 234) : new Color(246, 247, 249);
            Color dark = countBadge ? new Color(39, 61, 45) : new Color(46, 50, 56);
            g.setColor(new JBColor(light, dark));
            g.fillRoundRect(0, 1, getWidth() - 1, getHeight() - 2, 12, 12);
            g.setColor(new JBColor(accent, accent.darker()));
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(0, 1, getWidth() - 1, getHeight() - 2, 12, 12);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class WavyLabel extends JLabel {
        private boolean wave;
        private WavyLabel(String text) { super(text); }
        private void setWave(boolean wave) { this.wave = wave; }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!wave) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(JBColor.RED);
            int baseline = getInsets().top + getFontMetrics(getFont()).getAscent() + 2;
            for (int x = 2; x < getWidth() - 2; x += 4) {
                g.drawLine(x, baseline, x + 2, baseline + 2);
                g.drawLine(x + 2, baseline + 2, x + 4, baseline);
            }
            g.dispose();
        }
    }

    private static Color statusColor(WorktreeEntry entry) {
        return switch (entry.health()) {
            case HEALTHY, MAIN -> JBColor.GREEN;
            case LOCKED -> JBColor.BLUE;
            case DETACHED -> JBColor.GRAY;
            case MISSING -> JBColor.RED;
        };
    }

    private static String statusKey(WorktreeEntry entry) {
        if (entry.bare()) return "status.bareShort";
        if (entry.main()) return "status.mainShort";
        if (entry.prunable() || !entry.pathExists()) return "status.missingShort";
        if (entry.locked()) return "status.lockedShort";
        if (entry.currentProject()) return "status.currentShort";
        return entry.detached() ? "status.detachedShort" : "status.linkedShort";
    }

    private static String classification(WorktreeEntry entry) {
        if (entry.bare()) return I18n.text("value.bare");
        if (entry.main()) return I18n.text("value.main");
        if (entry.detached()) return I18n.text("value.detached");
        return I18n.text("value.linked");
    }

    private static String yesNo(boolean value) { return I18n.text(value ? "value.yes" : "value.no"); }

    private static String displayBranch(String branch) {
        return branch.startsWith("(") ? I18n.text("value.noBranch") : branch;
    }

    private static final class PathDialog extends com.intellij.openapi.ui.DialogWrapper {
        private final Project project;
        private final javax.swing.JTextField pathField;
        PathDialog(Project project, String path) {
            super(project);
            this.project = project;
            pathField = new javax.swing.JTextField(path, 42);
            setTitle(I18n.text("title.move"));
            setOKButtonText(I18n.text("action.move"));
            setCancelButtonText(I18n.text("button.cancel"));
            init();
        }
        String path() { return pathField.getText(); }
        @Override protected javax.swing.JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.add(new javax.swing.JLabel(I18n.text("dialog.move")), BorderLayout.NORTH);
            panel.add(pathField, BorderLayout.CENTER);
            JButton browse = new JButton(I18n.text("action.browse"));
            browse.addActionListener(event -> {
                VirtualFile selected = FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null);
                if (selected != null) pathField.setText(selected.getPath());
            });
            panel.add(browse, BorderLayout.EAST);
            return panel;
        }
    }
}
