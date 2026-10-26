package com.miwi.gitworktree.service;

import com.miwi.gitworktree.model.WorktreeEntry;

import java.util.ArrayList;
import java.util.List;

/** Parses Git's porcelain worktree listing without relying on IDE services. */
public final class WorktreeListParser {
    private WorktreeListParser() {}

    public static List<WorktreeEntry> parse(String repositoryRoot, String output) {
        List<MutableEntry> parsed = new ArrayList<>();
        MutableEntry current = null;
        for (String line : output.split("\\R")) {
            if (line.isBlank()) {
                current = null;
            } else if (line.startsWith("worktree ")) {
                current = new MutableEntry();
                current.path = line.substring("worktree ".length());
                parsed.add(current);
            } else if (current != null && line.startsWith("HEAD ")) {
                current.head = line.substring("HEAD ".length());
            } else if (current != null && line.startsWith("branch ")) {
                String ref = line.substring("branch ".length());
                current.branch = ref.startsWith("refs/heads/") ? ref.substring("refs/heads/".length()) : ref;
            } else if (current != null && line.equals("detached")) {
                current.detached = true;
            } else if (current != null && line.equals("bare")) {
                current.bare = true;
            } else if (current != null && line.startsWith("locked")) {
                current.locked = true;
            } else if (current != null && line.startsWith("prunable")) {
                current.prunable = true;
            }
        }

        boolean bareRepository = parsed.stream().anyMatch(entry -> entry.bare);
        List<WorktreeEntry> result = new ArrayList<>(parsed.size());
        for (int index = 0; index < parsed.size(); index++) {
            MutableEntry entry = parsed.get(index);
            if (entry.path == null || entry.path.isBlank()) continue;
            String branch = entry.branch;
            if (branch == null || branch.isBlank()) branch = entry.detached ? "(detached HEAD)" : "(no branch)";
            // A bare repository has no primary checkout; its first listed record must not gain main-worktree protections.
            boolean main = !bareRepository && index == 0 && !entry.bare;
            result.add(new WorktreeEntry(repositoryRoot, entry.path, branch,
                    entry.head == null ? "" : entry.head, main, entry.detached, entry.bare, entry.locked, entry.prunable));
        }
        return result;
    }

    private static final class MutableEntry {
        private String path;
        private String branch;
        private String head;
        private boolean detached;
        private boolean bare;
        private boolean locked;
        private boolean prunable;
    }
}
