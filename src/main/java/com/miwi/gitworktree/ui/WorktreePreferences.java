package com.miwi.gitworktree.ui;

import com.intellij.ide.util.PropertiesComponent;

/** Stores UI preferences shared by all projects in this IDE installation. */
public final class WorktreePreferences {
    private static final String SHOW_LIST_HELP_KEY = "git.worktree.sport.showListHelp";
    private static final String SHOW_PROTECTED_ACTIONS_KEY = "git.worktree.sport.showProtectedActions";

    private WorktreePreferences() {}

    public static boolean showListHelp() {
        return Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(SHOW_LIST_HELP_KEY, "true"));
    }

    public static void setShowListHelp(boolean visible) {
        PropertiesComponent.getInstance().setValue(SHOW_LIST_HELP_KEY, Boolean.toString(visible));
    }

    public static boolean showProtectedActions() {
        return Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(SHOW_PROTECTED_ACTIONS_KEY, "false"));
    }

    public static void setShowProtectedActions(boolean visible) {
        PropertiesComponent.getInstance().setValue(SHOW_PROTECTED_ACTIONS_KEY, Boolean.toString(visible));
    }
}
