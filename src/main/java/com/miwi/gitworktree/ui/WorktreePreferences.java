package com.miwi.gitworktree.ui;

import com.intellij.ide.util.PropertiesComponent;

/** Stores UI preferences shared by all projects in this IDE installation. */
public final class WorktreePreferences {
    private static final String SHOW_LIST_HELP_KEY = "git.worktree.sport.showListHelp";
    // Reuse the former visibility preference without weakening service-level protection.
    private static final String SHOW_MAIN_PROTECTED_ACTIONS_KEY = "git.worktree.sport.showProtectedActions";
    private static final String HIDE_MOVE_DELETE_ACTIONS_KEY = "git.worktree.sport.hideMoveDeleteActions";

    private WorktreePreferences() {}

    public static boolean showListHelp() {
        return Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(SHOW_LIST_HELP_KEY, "true"));
    }

    public static void setShowListHelp(boolean visible) {
        PropertiesComponent.getInstance().setValue(SHOW_LIST_HELP_KEY, Boolean.toString(visible));
    }

    public static boolean showMainProtectedActions() {
        return Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(SHOW_MAIN_PROTECTED_ACTIONS_KEY, "false"));
    }

    public static void setShowMainProtectedActions(boolean visible) {
        PropertiesComponent.getInstance().setValue(SHOW_MAIN_PROTECTED_ACTIONS_KEY, Boolean.toString(visible));
    }

    public static boolean hideMoveDeleteActions() {
        return Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(HIDE_MOVE_DELETE_ACTIONS_KEY, "false"));
    }

    public static void setHideMoveDeleteActions(boolean hidden) {
        PropertiesComponent.getInstance().setValue(HIDE_MOVE_DELETE_ACTIONS_KEY, Boolean.toString(hidden));
    }
}
