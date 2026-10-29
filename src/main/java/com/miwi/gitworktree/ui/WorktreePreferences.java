package com.miwi.gitworktree.ui;

import com.intellij.ide.util.PropertiesComponent;

/** Stores UI preferences shared by all projects in this IDE installation. */
public final class WorktreePreferences {
    private static final String SHOW_LIST_HELP_KEY = "git.worktree.sport.showListHelp";
    private static final String HIDE_MOVE_DELETE_ACTIONS_KEY = "git.worktree.sport.hideMoveDeleteActions";
    private static final String PARALLEL_BRANCH_PREFIX_KEY = "git.worktree.sport.parallelBranchPrefix";

    private WorktreePreferences() {}

    public static boolean showListHelp() {
        return Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(SHOW_LIST_HELP_KEY, "true"));
    }

    public static void setShowListHelp(boolean visible) {
        PropertiesComponent.getInstance().setValue(SHOW_LIST_HELP_KEY, Boolean.toString(visible));
    }

    public static boolean hideMoveDeleteActions() {
        return Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(HIDE_MOVE_DELETE_ACTIONS_KEY, "false"));
    }

    public static void setHideMoveDeleteActions(boolean hidden) {
        PropertiesComponent.getInstance().setValue(HIDE_MOVE_DELETE_ACTIONS_KEY, Boolean.toString(hidden));
    }

    /** Prefix is applied only when the creation form creates a new named branch. */
    public static String parallelBranchPrefix() {
        return normalizeParallelBranchPrefix(PropertiesComponent.getInstance()
                .getValue(PARALLEL_BRANCH_PREFIX_KEY, "sync-git"));
    }

    public static void setParallelBranchPrefix(String prefix) {
        PropertiesComponent.getInstance().setValue(PARALLEL_BRANCH_PREFIX_KEY,
                normalizeParallelBranchPrefix(prefix));
    }

    public static String parallelBranchName(String branchName) {
        String prefix = parallelBranchPrefix();
        String suffix = branchName == null ? "" : branchName.trim();
        while (suffix.startsWith("/")) suffix = suffix.substring(1);
        while (!prefix.isEmpty() && suffix.startsWith(prefix + "/")) {
            suffix = suffix.substring(prefix.length() + 1);
        }
        return prefix.isEmpty() ? suffix : prefix + "/" + suffix;
    }

    private static String normalizeParallelBranchPrefix(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim().replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
