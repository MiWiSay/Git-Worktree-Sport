package com.miwi.gitworktree.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Filesystem-aware identity helpers shared by worktree discovery and UI targeting. */
public final class WorktreePaths {
    private WorktreePaths() {}

    /**
     * Resolves existing path segments through symlinks while preserving any missing suffix.
     * Path equality and case handling are then delegated to the active filesystem provider.
     */
    public static Path identity(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path existingAncestor = absolute;
        while (existingAncestor != null) {
            if (Files.exists(existingAncestor)) {
                try {
                    Path realAncestor = existingAncestor.toRealPath();
                    return realAncestor.resolve(existingAncestor.relativize(absolute)).normalize();
                } catch (IOException | SecurityException ignored) {
                    // Try a shorter existing ancestor; inaccessible paths still get lexical normalization.
                }
            }
            existingAncestor = existingAncestor.getParent();
        }
        return absolute;
    }

    public static boolean same(Path first, Path second) {
        return identity(first).equals(identity(second));
    }

    /** Returns true when candidate is the same path as, or is nested below, ancestor. */
    public static boolean sameOrAncestor(Path ancestor, Path candidate) {
        Path normalizedAncestor = identity(ancestor);
        Path normalizedCandidate = identity(candidate);
        return normalizedCandidate.equals(normalizedAncestor) || normalizedCandidate.startsWith(normalizedAncestor);
    }
}
