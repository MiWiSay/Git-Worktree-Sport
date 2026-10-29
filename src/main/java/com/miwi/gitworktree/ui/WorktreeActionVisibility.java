package com.miwi.gitworktree.ui;

import com.miwi.gitworktree.model.WorktreeEntry;

/** Shared presentation policy for the tree popup and the IDE Git menu; never grants execution rights. */
public final class WorktreeActionVisibility {
    private WorktreeActionVisibility() {}

    public enum State { HIDDEN, ENABLED, DISABLED }

    public static State state(WorktreeEntry entry, String operation, boolean allowed) {
        if (operation.equals("move") || operation.equals("delete")) {
            // Git does not support moving/removing the primary working tree; no preference can enable it.
            if (entry.main() || WorktreePreferences.hideMoveDeleteActions()) return State.HIDDEN;
        }
        if (operation.equals("lock") || operation.equals("unlock")) {
            // Git rejects lock/unlock for its primary working tree, regardless of plugin preferences.
            if (entry.main()) return State.HIDDEN;
            // Show only the action matching Git's actual lock state, even for a protected checkout.
            if (operation.equals("unlock") != entry.locked()) return State.HIDDEN;
            return allowed ? State.ENABLED : State.DISABLED;
        }
        return allowed ? State.ENABLED : State.HIDDEN;
    }
}
