package com.miwi.gitworktree.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.miwi.gitworktree.ui.WorktreesPanel;
import com.miwi.gitworktree.ui.I18n;
import java.nio.file.Path;

public final class OpenWorktreeManagerAction extends AnAction {
    public static final String TOOL_WINDOW_ID = "Worktree Sport";

    public OpenWorktreeManagerAction() {
        super(() -> I18n.text("action.refresh"));
    }

    @Override
    public void update(AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null);
        event.getPresentation().setText(I18n.text("action.refresh"));
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        showAndReveal(project, null);
    }

    /** 显示完成后再访问面板，以兼容工具窗口首次按需初始化。 */
    public static void showAndReveal(Project project, Path createdPath) {
        if (project == null || project.isDisposed()) return;
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow == null) return;
        toolWindow.activate(() -> {
            if (project.isDisposed()) return;
            for (var content : toolWindow.getContentManager().getContents()) {
                if (content.getComponent() instanceof WorktreesPanel panel) {
                    if (createdPath == null) panel.refresh();
                    else panel.refreshAndReveal(createdPath);
                    return;
                }
            }
        });
    }
}
