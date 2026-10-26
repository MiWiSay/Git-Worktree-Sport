package com.miwi.gitworktree.model;

public record WorktreeEntry(
        String repositoryRoot,
        String path,
        String branch,
        String head,
        boolean main,
        boolean detached,
        boolean bare,
        boolean locked,
        boolean prunable,
        boolean currentProject
) {
    public WorktreeEntry(String repositoryRoot, String path, String branch, String head, boolean main,
                         boolean detached, boolean bare, boolean locked, boolean prunable) {
        this(repositoryRoot, path, branch, head, main, detached, bare, locked, prunable, false);
    }

    public WorktreeEntry withCurrentProject(boolean currentProject) {
        return new WorktreeEntry(repositoryRoot, path, branch, head, main, detached, bare, locked, prunable, currentProject);
    }

    public boolean protectedFromMoveOrDelete() {
        return main || currentProject || locked;
    }

    public boolean protectedFromPrune() {
        return main || currentProject || locked;
    }

    public boolean protectedFromGitLockChanges() {
        return main || currentProject;
    }

    public boolean protectedFromOpenWorktree() {
        // 主工作区可以在切换到其他工作区后重新打开；只有当前 IDE 已经打开的项目需要隐藏重复打开入口。
        return currentProject;
    }

    public boolean canOpenFolder() {
        return java.nio.file.Files.isDirectory(java.nio.file.Path.of(path));
    }

    public boolean canOpenWorktree() {
        return !protectedFromOpenWorktree() && !bare && canOpenFolder();
    }

    public CleanupDeleteAction cleanupDeleteAction() {
        if (protectedFromMoveOrDelete()) return CleanupDeleteAction.PROTECTED;
        return prunable || !pathExists() ? CleanupDeleteAction.PRUNE : CleanupDeleteAction.DELETE;
    }

    public boolean effectivelyLocked() {
        // 主工作树的插件保护不调用 Git 锁定；详情只显示 Git 实际记录的状态。
        return locked;
    }

    public String shortHead() {
        return head.length() > 8 ? head.substring(0, 8) : head;
    }

    public boolean pathExists() {
        return java.nio.file.Files.exists(java.nio.file.Path.of(path));
    }

    public Health health() {
        if (prunable || !pathExists()) return Health.MISSING;
        if (locked) return Health.LOCKED;
        if (main) return Health.MAIN;
        if (detached) return Health.DETACHED;
        return Health.HEALTHY;
    }

    public enum Health {
        HEALTHY,
        MAIN,
        LOCKED,
        DETACHED,
        MISSING
    }

    public enum CleanupDeleteAction {
        PROTECTED,
        PRUNE,
        DELETE
    }
}
