package git4idea.config;

/** Test-only executable selection; all Git processes still use the production command runner. */
public final class GitVcsApplicationSettings {
    private static final GitVcsApplicationSettings INSTANCE = new GitVcsApplicationSettings();
    public static GitVcsApplicationSettings getInstance() { return INSTANCE; }
    public String getSavedPathToGit() { return System.getProperty("git.worktree.sport.testGit", "git"); }
}
