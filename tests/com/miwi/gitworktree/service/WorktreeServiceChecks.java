package com.miwi.gitworktree.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.miwi.gitworktree.model.WorktreeEntry;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runs production service/command/parser code against disposable real Git repositories. */
public final class WorktreeServiceChecks {
    private static int assertions;
    private static Path root;

    public static void main(String[] args) throws Exception {
        Path base = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(base);
        root = base.resolve("repository with spaces");
        Files.createDirectory(root);
        git("init", "-b", "main");
        git("config", "user.name", "Compatibility Test");
        git("config", "user.email", "test@example.invalid");
        git("config", "gc.worktreePruneExpire", "now");
        Files.writeString(root.resolve("tracked.txt"), "initial\n");
        git("add", ".");
        git("commit", "-m", "Initial test fixture");
        var candidate = new WorktreeService.CreateSpec(WorktreeService.CreateMode.NEW_BRANCH,
                "validation-new", "refs/heads/main", false);
        Path candidatePath = base.resolve("validation workspace");
        check(WorktreeService.localBranchExists(root, "main"), "Existing local branch detected without IDE caches");
        check(!WorktreeService.localBranchExists(root, "validation-new"), "Unused local name is available");
        check(WorktreeService.validateCreateInputs(root, candidatePath.toString(), candidate).isEmpty(),
                "New path and unused name pass preflight");
        check(!Files.exists(candidatePath) && !WorktreeService.localBranchExists(root, "validation-new"),
                "Preflight never creates files or branch refs");
        Path occupiedValidation = base.resolve("pre-existing empty folder");
        Files.createDirectory(occupiedValidation);
        var duplicateName = new WorktreeService.CreateSpec(WorktreeService.CreateMode.NEW_BRANCH,
                "main", "refs/heads/main", false);
        var duplicateIssues = WorktreeService.validateCreateInputs(root, occupiedValidation.toString(), duplicateName);
        check(duplicateIssues.size() == 2 && duplicateIssues.stream().anyMatch(i -> i.field() == WorktreeService.CreateField.PATH)
                        && duplicateIssues.stream().anyMatch(i -> i.field() == WorktreeService.CreateField.BRANCH),
                "Empty existing target and branch collisions reported together");
        check(WorktreeService.validateCreateInputs(root, "", candidate).getFirst().field() == WorktreeService.CreateField.PATH,
                "Blank path is not interpreted as working directory");
        var invalidName = new WorktreeService.CreateSpec(WorktreeService.CreateMode.NEW_BRANCH,
                "bad name", "refs/heads/main", false);
        check(WorktreeService.validateCreateInputs(root, candidatePath.toString(), invalidName).getFirst().field()
                        == WorktreeService.CreateField.BRANCH, "Git validates new branch syntax");
        var invalidRef = new WorktreeService.CreateSpec(WorktreeService.CreateMode.NEW_BRANCH,
                "validation-new", "does-not-exist", false);
        check(WorktreeService.validateCreateInputs(root, candidatePath.toString(), invalidRef).getFirst().field()
                        == WorktreeService.CreateField.START_POINT, "Invalid commit reference is field-specific");
        git("branch", "prefix/child");
        check(!WorktreeService.validateCreateInputs(root, candidatePath.toString(),
                new WorktreeService.CreateSpec(WorktreeService.CreateMode.NEW_BRANCH, "prefix", "HEAD", false)).isEmpty(),
                "Parent ref namespace collision detected");
        check(!WorktreeService.validateCreateInputs(root, candidatePath.toString(),
                new WorktreeService.CreateSpec(WorktreeService.CreateMode.NEW_BRANCH, "main/child", "HEAD", false)).isEmpty(),
                "Child ref namespace collision detected");
        check(WorktreeService.validateCreateInputs(root, base.resolve("absent-parent/child").toString(), candidate)
                        .getFirst().field() == WorktreeService.CreateField.PATH, "Missing target parent rejected");
        check(WorktreeService.validateCreateInputs(root, root.resolve("tracked.txt").toString(), candidate)
                        .getFirst().field() == WorktreeService.CreateField.PATH, "File target rejected");
        check(WorktreeService.creationBranches(root).stream().anyMatch(ref -> ref.reference().equals("refs/heads/main")
                        && !ref.remote()), "Source options read full local refs");
        var initial = entry(root);
        check(initial.main() && !initial.locked(), "Main checkout is protected without a real Git lock");
        check(!WorktreeService.canChangeGitLock(initial), "Main checkout does not expose Git locking");
        rejects(() -> WorktreeService.remove(null, initial), "Main checkout cannot be deleted");
        rejects(() -> WorktreeService.move(null, initial, base.resolve("bad-main-move")), "Main checkout cannot be moved");
        rejects(() -> WorktreeService.lock(initial, "test"), "Main checkout cannot be locked");

        var branch = new WorktreeService.BranchSpec("existing", null, false);
        git("branch", "existing");
        Path existingPath = base.resolve("existing workspace");
        WorktreeService.create(root, existingPath, branch);
        check(Files.exists(existingPath.resolve("tracked.txt")), "Default creation checks out files");
        check(entry(existingPath).branch().equals("existing"), "Existing branch checkout");
        check(WorktreeService.inspectCreate(root, existingPath, branch).type()
                == WorktreeService.CreateConflictType.ACTIVE_WORKTREE, "Active registration is not duplicated");
        check(WorktreeService.inspectCreate(root, base.resolve("same-branch"), branch).type()
                == WorktreeService.CreateConflictType.BRANCH_IN_USE, "Already checked-out branch is detected");
        Path duplicate = base.resolve("forced-same-branch");
        WorktreeService.create(root, duplicate, branch, WorktreeService.CreateResolution.FORCE);
        check(entry(duplicate).branch().equals("existing"), "Explicit forced checkout works");
        WorktreeService.remove(null, entry(duplicate));

        var newSpec = new WorktreeService.CreateSpec(WorktreeService.CreateMode.NEW_BRANCH,
                "sync-git/feature/new", "main", false);
        Path newPath = base.resolve("new workspace");
        WorktreeService.create(root, newPath, branch, WorktreeService.CreateResolution.NORMAL, newSpec);
        check(entry(newPath).branch().equals("sync-git/feature/new"),
                "Parallel namespace branch is created from the selected start point");
        Path detached = base.resolve("detached");
        WorktreeService.create(root, detached, branch, WorktreeService.CreateResolution.NORMAL,
                new WorktreeService.CreateSpec(WorktreeService.CreateMode.DETACHED, "", "HEAD", false));
        check(entry(detached).detached(), "Detached checkout");
        Path orphan = base.resolve("orphan");
        WorktreeService.create(root, orphan, branch, WorktreeService.CreateResolution.NORMAL,
                new WorktreeService.CreateSpec(WorktreeService.CreateMode.ORPHAN, "empty-history", "", false));
        check(entry(orphan).branch().equals("empty-history") && !Files.exists(orphan.resolve("tracked.txt")),
                "Orphan checkout has no inherited files");
        check(!WorktreeService.validateCreateInputs(root, candidatePath.toString(),
                new WorktreeService.CreateSpec(WorktreeService.CreateMode.ORPHAN, "empty-history", "", false)).isEmpty(),
                "Unborn registered branch name cannot be duplicated");
        Path noCheckout = base.resolve("no-checkout");
        WorktreeService.create(root, noCheckout, branch, WorktreeService.CreateResolution.NORMAL,
                new WorktreeService.CreateSpec(WorktreeService.CreateMode.NEW_BRANCH, "lazy", "HEAD", true));
        check(Files.exists(noCheckout.resolve(".git")) && !Files.exists(noCheckout.resolve("tracked.txt")),
                "Advanced no-checkout registers without populating files");

        Path remote = base.resolve("remote.git");
        git("clone", "--bare", root.toString(), remote.toString());
        git("remote", "add", "origin", remote.toString());
        git("fetch", "origin");
        git("update-ref", "refs/remotes/origin/remote-feature", "HEAD");
        git("symbolic-ref", "refs/remotes/origin/HEAD", "refs/remotes/origin/main");
        check(WorktreeService.creationBranches(root).stream().anyMatch(ref -> ref.remote()
                        && ref.reference().equals("refs/remotes/origin/remote-feature")), "Remote source choices carry full refs");
        check(WorktreeService.creationBranches(root).stream().noneMatch(ref -> ref.reference().equals("refs/remotes/origin/HEAD")),
                "Symbolic remote HEAD not duplicated as a branch choice");
        var remoteBranch = new WorktreeService.BranchSpec("remote-feature", "refs/remotes/origin/remote-feature", true);
        Path remotePath = base.resolve("remote-tracking");
        WorktreeService.create(root, remotePath, remoteBranch);
        check(git("config", "branch.remote-feature.remote").equals("origin"), "Remote checkout configures upstream");
        WorktreeService.remove(null, entry(remotePath));
        WorktreeService.create(root, remotePath, remoteBranch);
        check(entry(remotePath).branch().equals("remote-feature"), "Remote selection reuses existing local branch");

        var linked = entry(existingPath);
        WorktreeService.lock(linked, "integration test");
        var locked = entry(existingPath);
        check(locked.locked(), "Git lock state is read back");
        rejects(() -> WorktreeService.forceRemove(null, locked), "Locked worktree cannot be force deleted");
        rejects(() -> WorktreeService.move(null, locked, base.resolve("bad-locked-move")), "Locked worktree cannot be moved");
        WorktreeService.unlock(locked);
        check(!entry(existingPath).locked(), "Git unlock state is read back");
        Path moved = base.resolve("moved workspace");
        WorktreeService.move(null, entry(existingPath), moved);
        check(Files.exists(moved.resolve("tracked.txt")) && !Files.exists(existingPath), "Move updates files and registration");
        WorktreeService.repair(entry(moved));
        check(entry(moved).branch().equals("existing"), "Repair keeps healthy metadata valid");
        var beforeExternalMove = entry(newPath);
        Path externalMoved = base.resolve("externally moved workspace");
        Files.move(newPath, externalMoved);
        WorktreeService.repair(new WorktreeEntry(beforeExternalMove.repositoryRoot(), externalMoved.toString(),
                beforeExternalMove.branch(), beforeExternalMove.head(), false, false, false, false, false));
        check(entry(externalMoved).branch().equals("sync-git/feature/new"),
                "Repair reconnects the namespaced branch after an external directory move");
        newPath = externalMoved;

        Project opened = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(), new Class<?>[]{Project.class},
                (proxy, method, values) -> switch (method.getName()) {
                    case "getBasePath" -> moved.toString();
                    case "isDisposed" -> false;
                    default -> null;
                });
        ProjectManager.projects = new Project[]{opened};
        check(!WorktreeService.canOpenWorktree(entry(moved)), "Already-open project does not offer Open Worktree");
        rejects(() -> WorktreeService.remove(null, entry(moved)), "Open linked project cannot be deleted");
        rejects(() -> WorktreeService.move(null, entry(moved), base.resolve("bad-open-move")), "Open linked project cannot be moved");
        ProjectManager.projects = new Project[0];
        Files.writeString(moved.resolve("untracked.txt"), "must not silently discard\n");
        check(WorktreeService.canOpenWorktree(entry(moved)), "Closed valid worktree can be opened");
        rejects(() -> WorktreeService.remove(null, entry(moved)), "Untracked content requires separate force confirmation");
        check(Files.exists(moved.resolve("untracked.txt")), "Rejected normal removal preserves user files");
        WorktreeService.forceRemove(null, entry(moved));
        check(!Files.exists(moved), "Confirmed forced removal deletes directory");
        check(WorktreeService.listForRepository(root).stream().noneMatch(e -> e.branch().equals("existing")),
                "Forced removal clears registration");
        Path modified = base.resolve("modified tracked");
        WorktreeService.create(root, modified, branch);
        Files.writeString(modified.resolve("tracked.txt"), "modified tracked content\n");
        rejects(() -> WorktreeService.remove(null, entry(modified)), "Modified tracked content is protected");
        WorktreeService.forceRemove(null, entry(modified));
        check(!Files.exists(modified), "Confirmed removal handles modified tracked content");

        Path stale = base.resolve("missing");
        WorktreeService.create(root, stale, branch);
        // Only delete known fixture files. Never traverse or remove user paths.
        Files.delete(stale.resolve("tracked.txt"));
        Files.delete(stale.resolve(".git"));
        Files.delete(stale);
        var staleEntry = entry(stale);
        check(WorktreeService.inspectCreate(root, stale, branch).type()
                == WorktreeService.CreateConflictType.STALE_REGISTRATION, "Missing-directory registration is detected");
        WorktreeService.create(root, stale, branch, WorktreeService.CreateResolution.PRUNE_THEN_CREATE);
        check(Files.exists(stale.resolve("tracked.txt")), "Confirmed prune-then-create restores missing workspace");
        Files.delete(stale.resolve("tracked.txt"));
        Files.delete(stale.resolve(".git"));
        Files.delete(stale);
        staleEntry = entry(stale);
        check(WorktreeService.cleanupDeleteAction(staleEntry) == WorktreeEntry.CleanupDeleteAction.PRUNE,
                "Missing registration offers Prune rather than Delete");
        check(!WorktreeService.prunePreview(staleEntry).isBlank(), "Prune previews stale registration");
        WorktreeService.prune(staleEntry);
        check(WorktreeService.listForRepository(root).stream().noneMatch(e -> WorktreePaths.same(Path.of(e.path()), stale)),
                "Prune removes stale metadata");
        check(Files.exists(newPath.resolve("tracked.txt")), "Prune leaves healthy worktree files intact");
        WorktreeEntry healthy = entry(newPath);
        rejects(() -> WorktreeService.prune(healthy), "Healthy worktree cannot be pruned");
        Path bare = base.resolve("bare repository.git");
        git("clone", "--bare", root.toString(), bare.toString());
        var bareEntries = WorktreeService.listForRepository(bare);
        check(bareEntries.getFirst().bare() && !bareEntries.getFirst().main(), "Bare repository is not a primary checkout");
        Path bareLinked = base.resolve("linked from bare");
        WorktreeService.create(bare, bareLinked, new WorktreeService.BranchSpec("main", null, false));
        var bareChild = WorktreeService.listForRepository(bare).stream().filter(e -> !e.bare()).findFirst().orElseThrow();
        check(!bareChild.main() && Files.exists(bareLinked.resolve("tracked.txt")), "Checkout from bare repository is linked");
        WorktreeService.remove(null, bareChild);
        check(!Files.exists(bareLinked), "Linked checkout from bare repository can be removed");
        Path occupied = base.resolve("occupied");
        Files.createDirectory(occupied);
        Files.writeString(occupied.resolve("keep.txt"), "keep");
        check(WorktreeService.inspectCreate(root, occupied, branch).type()
                == WorktreeService.CreateConflictType.NON_EMPTY_DIRECTORY, "Non-empty destination is detected");
        rejects(() -> WorktreeService.create(root, occupied, branch), "Non-empty destination is not overwritten");
        System.out.println("PASS: " + assertions + " real-Git production service assertions; " + git("--version"));
        System.out.println("Fixtures retained for inspection: " + base);
    }

    private static WorktreeEntry entry(Path path) throws Exception {
        return WorktreeService.listForRepository(root).stream()
                .filter(e -> WorktreePaths.same(Path.of(e.path()), path)).findFirst().orElseThrow();
    }

    private static String git(String... args) throws Exception {
        return GitCommandRunner.requireSuccess(root, args);
    }

    private static void rejects(Checked operation, String message) throws Exception {
        try { operation.run(); } catch (java.io.IOException expected) { assertions++; return; }
        throw new AssertionError(message);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        assertions++;
    }

    private interface Checked { void run() throws Exception; }
}
