package com.miwi.gitworktree.service;

/** Git 普通删除因工作区内容被保护时，供界面决定是否展示二次强制删除确认。 */
public final class WorktreeRemovalPolicy {
    private static final String MODIFIED_OR_UNTRACKED = "contains modified or untracked files";

    private WorktreeRemovalPolicy() {}

    public static boolean allowsForceRetry(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains(MODIFIED_OR_UNTRACKED)) {
                return true;
            }
        }
        return false;
    }
}
