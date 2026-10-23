package com.miwi.gitworktree.service;

import git4idea.config.GitVcsApplicationSettings;
import com.intellij.openapi.util.SystemInfo;
import com.miwi.gitworktree.ui.I18n;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GitCommandRunner {
    private GitCommandRunner() {}

    public static Result run(Path repositoryRoot, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(gitExecutable());
        command.add("-C");
        command.add(repositoryRoot.toString());
        command.addAll(Arrays.asList(arguments));

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output;
        try (var stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        int exitCode = process.waitFor();
        return new Result(exitCode, output);
    }

    public static String requireSuccess(Path repositoryRoot, String... arguments) throws IOException, InterruptedException {
        Result result = run(repositoryRoot, arguments);
        if (result.exitCode() != 0) {
            String detail = result.output().isBlank() ? I18n.format("error.gitExit", result.exitCode()) : result.output();
            throw new IOException(detail);
        }
        return result.output();
    }

    public static String requireSuccessWithLongPaths(Path repositoryRoot, String... arguments)
            throws IOException, InterruptedException {
        // core.longpaths is a Windows Git setting; keep the workaround local to Windows invocations.
        String[] command = arguments;
        if (SystemInfo.isWindows) {
            command = new String[arguments.length + 2];
            command[0] = "-c";
            command[1] = "core.longpaths=true";
            System.arraycopy(arguments, 0, command, 2, arguments.length);
        }
        Result result = run(repositoryRoot, command);
        if (result.exitCode() != 0) {
            String detail = result.output().isBlank() ? I18n.format("error.gitExit", result.exitCode()) : result.output();
            throw new IOException(detail);
        }
        return result.output();
    }

    private static String gitExecutable() {
        String configured = GitVcsApplicationSettings.getInstance().getSavedPathToGit();
        if (configured == null || configured.isBlank()) return "git";
        String path = configured.trim();
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            return path.substring(1, path.length() - 1);
        }
        return path;
    }

    public record Result(int exitCode, String output) {}
}
