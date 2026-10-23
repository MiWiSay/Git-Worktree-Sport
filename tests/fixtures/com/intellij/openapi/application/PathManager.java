package com.intellij.openapi.application;

import java.nio.file.Path;

/** Isolates regression-test language files from the user's actual IDE settings. */
public final class PathManager {
    private PathManager() {}

    public static String getConfigPath() {
        return System.getProperty("git.worktree.sport.testConfigDir",
                Path.of(System.getProperty("java.io.tmpdir"), "git-worktree-sport-test-config").toString());
    }
}
