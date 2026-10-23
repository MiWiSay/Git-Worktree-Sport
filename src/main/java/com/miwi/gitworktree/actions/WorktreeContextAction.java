package com.miwi.gitworktree.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.miwi.gitworktree.ui.I18n;
import com.miwi.gitworktree.ui.WorktreesPanel;
import com.miwi.gitworktree.model.WorktreeEntry;

public abstract class WorktreeContextAction extends AnAction {
    private final String operation;
    private final String labelKey;

    protected WorktreeContextAction(String operation, String label) {
        super(I18n.text(label));
        this.operation = operation;
        this.labelKey = label;
    }

    @Override public void update(AnActionEvent event) {
        event.getPresentation().setText(I18n.text(labelKey));
        WorktreesPanel panel = panel(event.getProject());
        WorktreeEntry entry = panel == null ? null : panel.selectedWorktree();
        boolean visible = entry != null;
        if (entry != null) {
            visible = switch (operation) {
                case "openWorktree" -> !entry.protectedFromOpenWorktree();
                case "lock" -> !entry.protectedFromGitLockChanges() && !entry.locked();
                case "unlock" -> !entry.protectedFromGitLockChanges() && entry.locked();
                case "move" -> entry.cleanupDeleteAction() == WorktreeEntry.CleanupDeleteAction.DELETE;
                case "delete" -> entry.cleanupDeleteAction() == WorktreeEntry.CleanupDeleteAction.DELETE;
                case "prune" -> entry.cleanupDeleteAction() == WorktreeEntry.CleanupDeleteAction.PRUNE;
                default -> true;
            };
        }
        event.getPresentation().setVisible(visible);
        event.getPresentation().setEnabled(visible);
    }

    @Override public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;
        WorktreesPanel panel = panel(project);
        if (panel != null) panel.performContextAction(operation);
    }

    private static WorktreesPanel panel(Project project) {
        if (project == null) return null;
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(OpenWorktreeManagerAction.TOOL_WINDOW_ID);
        if (window == null || window.getContentManager().getContents().length == 0) return null;
        return window.getContentManager().getContents()[0].getComponent() instanceof WorktreesPanel panel ? panel : null;
    }

    public static final class OpenWorktree extends WorktreeContextAction { public OpenWorktree() { super("openWorktree", "action.openWorktree"); } }
    public static final class OpenFolder extends WorktreeContextAction { public OpenFolder() { super("open", "action.open"); } }
    public static final class Lock extends WorktreeContextAction { public Lock() { super("lock", "action.lock"); } }
    public static final class Unlock extends WorktreeContextAction { public Unlock() { super("unlock", "action.unlock"); } }
    public static final class Repair extends WorktreeContextAction { public Repair() { super("repair", "action.repair"); } }
    public static final class Prune extends WorktreeContextAction { public Prune() { super("prune", "action.prune"); } }
    public static final class Move extends WorktreeContextAction { public Move() { super("move", "action.move"); } }
    public static final class Delete extends WorktreeContextAction { public Delete() { super("delete", "action.delete"); } }
}
