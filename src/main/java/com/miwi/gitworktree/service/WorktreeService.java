package com.miwi.gitworktree.service;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import git4idea.GitBranch;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import com.miwi.gitworktree.model.WorktreeEntry;
import com.miwi.gitworktree.ui.I18n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorktreeService {
    private WorktreeService() {}

    public static List<WorktreeEntry> list(Project project) throws Exception {
        Map<Path, WorktreeEntry> entriesByPath = new LinkedHashMap<>();
        // 管理入口属于当前项目，但用户希望一次看到 IDE 中其他已打开项目的 worktree。
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 0 && !project.isDisposed()) openProjects = new Project[]{project};
        for (Project openProject : openProjects) {
            if (openProject.isDisposed()) continue;
            String activePath = openProject.getBasePath();
            for (GitRepository repository : git4idea.repo.GitRepositoryManager.getInstance(openProject).getRepositories()) {
                Path root = WorktreePaths.identity(Path.of(repository.getRoot().getPath()));
                String output = GitCommandRunner.requireSuccess(root, "worktree", "list", "--porcelain");
                for (WorktreeEntry entry : parse(root.toString(), output)) {
                    boolean currentProject = activePath != null
                            && WorktreePaths.sameOrAncestor(Path.of(entry.path()), Path.of(activePath));
                    WorktreeEntry labeledEntry = entry.withCurrentProject(currentProject);
                    Path key = WorktreePaths.identity(Path.of(entry.path()));
                    WorktreeEntry existing = entriesByPath.get(key);
                    if (existing == null || (currentProject && !existing.currentProject())) {
                        entriesByPath.put(key, labeledEntry);
                    }
                }
            }
        }
        return new ArrayList<>(entriesByPath.values());
    }

    public static List<WorktreeEntry> listForRepository(Path repositoryRoot) throws Exception {
        Path root = WorktreePaths.identity(repositoryRoot);
        return parse(root.toString(), GitCommandRunner.requireSuccess(root, "worktree", "list", "--porcelain"));
    }

    public static BranchSpec resolveBranch(GitBranch branch, GitRepository repository) throws IOException {
        if (!branch.isRemote()) {
            return new BranchSpec(branch.getName(), null, false);
        }

        String fullName = branch.getFullName();
        String remotePrefix = "refs/remotes/";
        if (!fullName.startsWith(remotePrefix)) {
            throw new IOException(I18n.format("error.remoteReference", fullName));
        }
        String remoteAndBranch = fullName.substring(remotePrefix.length());
        for (GitRemote remote : repository.getRemotes()) {
            String prefix = remote.getName() + "/";
            if (remoteAndBranch.startsWith(prefix) && remoteAndBranch.length() > prefix.length()) {
                return new BranchSpec(remoteAndBranch.substring(prefix.length()), fullName, true);
            }
        }
        throw new IOException(I18n.format("error.remoteName", fullName));
    }

    public static CreateConflict inspectCreate(Path repositoryRoot, Path target, BranchSpec branch) throws Exception {
        return inspectCreate(repositoryRoot, target, branch, true);
    }

    public static CreateConflict inspectCreate(Path repositoryRoot, Path target, BranchSpec branch,
                                               boolean checkBranchAvailability) throws Exception {
        Path root = WorktreePaths.identity(repositoryRoot);
        Path destination = target.toAbsolutePath().normalize();
        for (WorktreeEntry entry : listForRepository(root)) {
            if (WorktreePaths.same(Path.of(entry.path()), destination)) {
                return entry.pathExists() && !entry.prunable()
                        ? new CreateConflict(CreateConflictType.ACTIVE_WORKTREE, entry)
                        : new CreateConflict(CreateConflictType.STALE_REGISTRATION, entry);
            }
            if (checkBranchAvailability && entry.branch().equals(branch.localName())) {
                return new CreateConflict(CreateConflictType.BRANCH_IN_USE, entry);
            }
        }
        if (Files.exists(destination) && !isEmptyDirectory(destination)) {
            return new CreateConflict(CreateConflictType.NON_EMPTY_DIRECTORY, null);
        }
        return new CreateConflict(CreateConflictType.NONE, null);
    }

    public static void create(Path repositoryRoot, Path target, BranchSpec branch) throws Exception {
        create(repositoryRoot, target, branch, CreateResolution.NORMAL);
    }

    public static void create(Path repositoryRoot, Path target, BranchSpec branch, CreateResolution resolution) throws Exception {
        create(repositoryRoot, target, branch, resolution,
                new CreateSpec(CreateMode.EXISTING_BRANCH, branch.localName(),
                        branch.startPoint() == null ? branch.localName() : branch.startPoint(), false));
    }

    public static void create(Path repositoryRoot, Path target, BranchSpec selectedBranch,
                              CreateResolution resolution, CreateSpec spec) throws Exception {
        Path root = WorktreePaths.identity(repositoryRoot);
        Path destination = target.toAbsolutePath().normalize();
        if (WorktreePaths.same(destination, root)) throw new IOException(I18n.text("error.targetRepositoryRoot"));
        Path parent = destination.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException(I18n.format("error.parentMissing", parent));
        }
        if (Files.exists(destination)) {
            if (!Files.isDirectory(destination)) throw new IOException(I18n.text("error.targetNotDirectory"));
            if (!isEmptyDirectory(destination)) throw new IOException(I18n.format("error.targetNotEmpty", destination));
        }

        if (resolution == CreateResolution.PRUNE_THEN_CREATE) {
            GitCommandRunner.requireSuccess(root, "worktree", "prune");
        }

        add(root, destination, selectedBranch, resolution == CreateResolution.FORCE, spec);
    }

    private static void add(Path root, Path destination, BranchSpec branch, boolean force, CreateSpec spec) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("worktree");
        args.add("add");
        if (force) args.add("--force");
        if (spec.noCheckout()) args.add("--no-checkout");
        switch (spec.mode()) {
            case EXISTING_BRANCH -> {
                if (branch.remote()) {
                    GitCommandRunner.Result localBranch = GitCommandRunner.run(root, "show-ref", "--verify", "--quiet", "refs/heads/" + branch.localName());
                    if (localBranch.exitCode() != 0) {
                        args.add("--track");
                        args.add("-b");
                        args.add(branch.localName());
                        args.add(destination.toString());
                        args.add(branch.startPoint());
                    } else {
                        args.add(destination.toString());
                        args.add(branch.localName());
                    }
                } else {
                    args.add(destination.toString());
                    args.add(branch.localName());
                }
            }
            case NEW_BRANCH -> {
                args.add("-b");
                args.add(spec.branchName());
                args.add(destination.toString());
                args.add(spec.startPoint());
            }
            case DETACHED -> {
                args.add("--detach");
                args.add(destination.toString());
                args.add(spec.startPoint());
            }
            case ORPHAN -> {
                args.add("--orphan");
                args.add("-b");
                args.add(spec.branchName());
                args.add(destination.toString());
            }
        }
        GitCommandRunner.requireSuccess(root, args.toArray(String[]::new));
    }

    public static void remove(Project project, WorktreeEntry entry) throws Exception {
        Path path = Path.of(entry.path()).toAbsolutePath().normalize();
        ensureCanRemove(entry);
        if (isOpenInIde(path)) throw new IOException(I18n.text("error.deleteOpenProject"));
        Path repositoryRoot = Path.of(entry.repositoryRoot()).toAbsolutePath().normalize();
        GitCommandRunner.requireSuccessWithLongPaths(repositoryRoot, "worktree", "remove", path.toString());
    }

    public static void forceRemove(Project project, WorktreeEntry entry) throws Exception {
        Path path = Path.of(entry.path()).toAbsolutePath().normalize();
        ensureCanRemove(entry);
        if (isOpenInIde(path)) throw new IOException(I18n.text("error.deleteOpenProject"));
        Path repositoryRoot = Path.of(entry.repositoryRoot()).toAbsolutePath().normalize();
        GitCommandRunner.requireSuccessWithLongPaths(repositoryRoot, "worktree", "remove", "--force", path.toString());
    }

    private static void ensureCanRemove(WorktreeEntry entry) throws IOException {
        if (entry.main()) throw new IOException(I18n.text("confirm.deleteMain"));
        if (isCurrentProjectWorktree(entry)) throw new IOException(I18n.text("error.currentWorktreeProtected"));
        if (entry.locked()) throw new IOException(I18n.text("error.lockedProtected"));
        if (entry.prunable() || !entry.pathExists()) throw new IOException(I18n.text("error.deleteStaleWorktree"));
    }

    private static void prune(Path repositoryRoot) throws Exception {
        GitCommandRunner.requireSuccess(repositoryRoot.toAbsolutePath().normalize(), "worktree", "prune");
    }

    public static String prunePreview(Path repositoryRoot) throws Exception {
        return GitCommandRunner.requireSuccess(repositoryRoot.toAbsolutePath().normalize(),
                "worktree", "prune", "--dry-run", "--verbose");
    }

    public static String prunePreview(WorktreeEntry selectedEntry) throws Exception {
        ensureCanPrune(selectedEntry);
        if (!selectedEntry.prunable() && selectedEntry.pathExists()) {
            throw new IOException(I18n.text("error.pruneHealthyWorktree"));
        }
        return prunePreview(Path.of(selectedEntry.repositoryRoot()));
    }

    public static void prune(WorktreeEntry selectedEntry) throws Exception {
        ensureCanPrune(selectedEntry);
        if (!selectedEntry.prunable() && selectedEntry.pathExists()) {
            throw new IOException(I18n.text("error.pruneHealthyWorktree"));
        }
        prune(Path.of(selectedEntry.repositoryRoot()));
    }

    public static void repair(WorktreeEntry entry) throws Exception {
        Path root = Path.of(entry.repositoryRoot()).toAbsolutePath().normalize();
        Path worktreePath = Path.of(entry.path()).toAbsolutePath().normalize();
        GitCommandRunner.requireSuccess(root, "worktree", "repair", worktreePath.toString());
    }

    public static void lock(WorktreeEntry entry, String reason) throws Exception {
        ensureCanChangeGitLock(entry);
        Path root = Path.of(entry.repositoryRoot()).toAbsolutePath().normalize();
        GitCommandRunner.requireSuccess(root, "worktree", "lock", entry.path(), "--reason", reason);
    }

    public static void unlock(WorktreeEntry entry) throws Exception {
        ensureCanChangeGitLock(entry);
        Path root = Path.of(entry.repositoryRoot()).toAbsolutePath().normalize();
        GitCommandRunner.requireSuccess(root, "worktree", "unlock", entry.path());
    }

    public static void move(Project project, WorktreeEntry entry, Path target) throws Exception {
        Path source = Path.of(entry.path()).toAbsolutePath().normalize();
        Path destination = target.toAbsolutePath().normalize();
        if (entry.main()) throw new IOException(I18n.text("confirm.moveMain"));
        if (isCurrentProjectWorktree(entry)) throw new IOException(I18n.text("error.currentWorktreeProtected"));
        if (entry.locked()) throw new IOException(I18n.text("error.lockedProtected"));
        if (entry.prunable() || !entry.pathExists()) throw new IOException(I18n.text("error.deleteStaleWorktree"));
        if (isOpenInIde(source)) throw new IOException(I18n.text("error.moveOpenProject"));
        if (destination.getParent() == null || !Files.isDirectory(destination.getParent())) throw new IOException(I18n.format("error.parentMissing", destination.getParent()));
        if (Files.exists(destination)) throw new IOException(I18n.format("error.targetExists", destination));
        Path root = Path.of(entry.repositoryRoot()).toAbsolutePath().normalize();
        GitCommandRunner.requireSuccess(root, "worktree", "move", source.toString(), destination.toString());
    }

    public static boolean isEmptyDirectory(Path path) throws IOException {
        if (!Files.exists(path)) return true;
        if (!Files.isDirectory(path)) return false;
        try (var children = Files.list(path)) {
            return children.findAny().isEmpty();
        }
    }

    public static boolean isOpenInIde(Path path) {
        for (Project openProject : ProjectManager.getInstance().getOpenProjects()) {
            String basePath = openProject.getBasePath();
            if (basePath != null && WorktreePaths.sameOrAncestor(path, Path.of(basePath))) return true;
        }
        return false;
    }

    public static boolean isCurrentProjectWorktree(WorktreeEntry entry) {
        return entry.currentProject() || isOpenInIde(Path.of(entry.path()).toAbsolutePath().normalize());
    }

    public static boolean canOpenWorktree(WorktreeEntry entry) {
        return entry.canOpenWorktree() && !isOpenInIde(Path.of(entry.path()));
    }

    public static boolean canOpenFolder(WorktreeEntry entry) {
        return entry.canOpenFolder();
    }

    public static boolean canChangeGitLock(WorktreeEntry entry) {
        return !entry.main() && !isCurrentProjectWorktree(entry);
    }

    public static WorktreeEntry.CleanupDeleteAction cleanupDeleteAction(WorktreeEntry entry) {
        if (entry.main() || isCurrentProjectWorktree(entry) || entry.locked()) {
            return WorktreeEntry.CleanupDeleteAction.PROTECTED;
        }
        return entry.prunable() || !entry.pathExists()
                ? WorktreeEntry.CleanupDeleteAction.PRUNE : WorktreeEntry.CleanupDeleteAction.DELETE;
    }

    /**
     * Returns the short explanation shown for a visible but disabled protected action.
     * Keep this in the service layer so the IDE action menu and the tool-window menu
     * describe the same protection state and cannot drift apart.
     */
    public static String protectedActionReason(WorktreeEntry entry, String operation) {
        boolean lockOperation = operation.equals("lock") || operation.equals("unlock");
        if (entry.main() && lockOperation) return I18n.text("menu.protectedReason.mainLock");
        if (entry.main()) return I18n.text("menu.protectedReason.main");
        if (isCurrentProjectWorktree(entry)) return I18n.text("menu.protectedReason.current");
        if (entry.locked()) return I18n.text("menu.protectedReason.locked");
        return I18n.text("menu.protectedReason.unavailable");
    }

    private static void ensureCanPrune(WorktreeEntry entry) throws IOException {
        if (entry.main()) throw new IOException(I18n.text("error.mainProtectedPrune"));
        if (isCurrentProjectWorktree(entry)) throw new IOException(I18n.text("error.currentWorktreeProtected"));
        if (entry.locked()) throw new IOException(I18n.text("error.lockedProtected"));
    }

    private static void ensureCanChangeGitLock(WorktreeEntry entry) throws IOException {
        if (entry.main()) throw new IOException(I18n.text("error.mainLockOperation"));
        if (isCurrentProjectWorktree(entry)) throw new IOException(I18n.text("error.currentWorktreeProtected"));
    }

    private static List<WorktreeEntry> parse(String repositoryRoot, String output) {
        return WorktreeListParser.parse(repositoryRoot, output);
    }

    public record BranchSpec(String localName, String startPoint, boolean remote) {}

    public enum CreateMode {
        EXISTING_BRANCH,
        NEW_BRANCH,
        DETACHED,
        ORPHAN
    }

    public record CreateSpec(CreateMode mode, String branchName, String startPoint, boolean noCheckout) {
        public CreateSpec {
            if (mode == null) throw new IllegalArgumentException("Creation mode is required.");
            if (mode == CreateMode.ORPHAN && noCheckout) {
                throw new IllegalArgumentException(I18n.text("create.noCheckoutOrphanHint"));
            }
            if (mode == CreateMode.NEW_BRANCH && (branchName == null || branchName.isBlank() || startPoint == null || startPoint.isBlank())) {
                throw new IllegalArgumentException("A branch name and start point are required.");
            }
            if (mode == CreateMode.DETACHED && (startPoint == null || startPoint.isBlank())) {
                throw new IllegalArgumentException("A commit or branch reference is required.");
            }
            if (mode == CreateMode.ORPHAN && (branchName == null || branchName.isBlank())) {
                throw new IllegalArgumentException("An orphan branch name is required.");
            }
        }
    }

    public enum CreateResolution {
        NORMAL,
        PRUNE_THEN_CREATE,
        FORCE
    }

    public enum CreateConflictType {
        NONE,
        STALE_REGISTRATION,
        ACTIVE_WORKTREE,
        BRANCH_IN_USE,
        NON_EMPTY_DIRECTORY
    }

    public record CreateConflict(CreateConflictType type, WorktreeEntry entry) {}

}
