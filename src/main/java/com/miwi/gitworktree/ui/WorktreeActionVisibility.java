package com.miwi.gitworktree.ui;

import com.miwi.gitworktree.model.WorktreeEntry;

/** Shared presentation policy for the tree popup and the IDE Git menu; never grants execution rights. */
public final class WorktreeActionVisibility {
    private WorktreeActionVisibility() {}

    public enum State { HIDDEN, ENABLED, DISABLED }

    public static State state(WorktreeEntry entry, String operation, boolean allowed) {
        if (operation.equals("move") || operation.equals("delete")) {
            // Global hiding wins when both checkboxes are selected. Neither option bypasses protection.
            if (WorktreePreferences.hideMoveDeleteActions()) return State.HIDDEN;
            if (entry.main()) return WorktreePreferences.showMainProtectedActions() ? State.DISABLED : State.HIDDEN;
        }
        if (operation.equals("lock") || operation.equals("unlock")) {
            // Show only the action matching Git's actual lock state, even for a protected checkout.
            if (operation.equals("unlock") != entry.locked()) return State.HIDDEN;
            return allowed ? State.ENABLED : State.DISABLED;
        }
        return allowed ? State.ENABLED : State.HIDDEN;
    }
}
