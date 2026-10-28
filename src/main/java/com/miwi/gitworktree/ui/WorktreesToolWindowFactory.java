package com.miwi.gitworktree.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

public final class WorktreesToolWindowFactory implements ToolWindowFactory, DumbAware {
    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        WorktreesPanel panel = new WorktreesPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, I18n.text("content.worktrees"), false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
        Runnable languageListener = () -> com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) content.setDisplayName(I18n.text("content.worktrees"));
        });
        I18n.addLanguageListener(languageListener);
        Disposer.register(content, () -> I18n.removeLanguageListener(languageListener));
    }
}
