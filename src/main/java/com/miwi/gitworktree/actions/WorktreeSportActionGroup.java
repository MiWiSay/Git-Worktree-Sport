package com.miwi.gitworktree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.miwi.gitworktree.ui.I18n;

public final class WorktreeSportActionGroup extends DefaultActionGroup {
    public WorktreeSportActionGroup() {
        super(I18n.text("menu.worktreeSport"), true);
    }

    @Override public void update(AnActionEvent event) {
        event.getPresentation().setText(I18n.text("menu.worktreeSport"));
        super.update(event);
    }
}
