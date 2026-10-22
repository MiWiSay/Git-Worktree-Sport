package com.miwi.gitworktree.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.testFramework.LightVirtualFile;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitStandardRemoteBranch;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;

import java.lang.reflect.Proxy;
import java.util.List;

/** Exercises the actual hierarchical popup model without opening windows or running Git. */
public final class BranchPopupChecks {
    private static int assertions;

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(), new Class<?>[]{Project.class},
                (proxy, method, values) -> method.getName().equals("isDisposed") ? false : null);
        GitRepository repository = repository("/repository/one");
        GitRepository other = repository("/repository/two");
        GitBranch local = new GitLocalBranch("feature/one");
        GitBranch remote = new GitStandardRemoteBranch(new GitRemote("origin", List.of("https://example.invalid/repo"),
                List.of(), List.of(), List.of()), "feature/two");
        var first = new BranchCreationPopup.BranchChoice(repository, local);
        var second = new BranchCreationPopup.BranchChoice(repository, new GitLocalBranch("feature/three"));
        var remoteChoice = new BranchCreationPopup.BranchChoice(repository, remote);
        var otherChoice = new BranchCreationPopup.BranchChoice(other, local);
        Class<?> type = Class.forName("com.miwi.gitworktree.ui.BranchCreationPopup$BranchStep");
        var constructor = type.getDeclaredConstructor(Project.class, List.class);
        constructor.setAccessible(true);
        ListPopupStep<BranchCreationPopup.BranchChoice> step = (ListPopupStep<BranchCreationPopup.BranchChoice>) constructor
                .newInstance(project, List.of(first, second, remoteChoice, otherChoice));
        check(step.isSpeedSearchEnabled(), "Native speed search enabled");
        check(!step.isAutoSelectionEnabled(), "Does not automatically execute the only branch");
        check(step.getValues().size() == 4, "Local, remote, and multi-repository choices retained");
        check(step.getTextFor(first).equals("feature/one"), "Branch name is intact");
        check(step.getTextFor(remoteChoice).equals("origin/feature/two"), "Remote name is intact");
        check(step.getSeparatorAbove(first) != null, "First repository group label shown");
        check(step.getSeparatorAbove(second) == null, "Same-kind branch does not duplicate separator");
        check(step.getSeparatorAbove(remoteChoice) != null, "Remote branch group has separator");
        check(step.getSeparatorAbove(otherChoice).getText().contains("/repository/two"), "Repository groups unambiguous");
        check(step.hasSubstep(first), "Branches expose right-side submenu");
        for (boolean click : new boolean[]{false, true}) {
            PopupStep<?> next = step.onChosen(first, click);
            check(next instanceof ListPopupStep<?>, "Hover/keyboard and click return submenu");
            var actions = (ListPopupStep<String>) next;
            check(actions.getValues().equals(List.of(I18n.text("branch.create"))), "Submenu has only Create Worktree");
            check(!actions.isAutoSelectionEnabled(), "Creating requires explicit confirmation of action");
            check(actions.onChosen(actions.getValues().getFirst(), true) == PopupStep.FINAL_CHOICE,
                    "Creation scheduled after popup closes");
            check(actions.getFinalRunnable() != null, "Shared creation callback provided");
        }
        System.out.println("PASS: " + assertions + " branch popup model assertions");
    }

    private static GitRepository repository(String path) {
        var root = new LightVirtualFile("repo") { @Override public String getPath() { return path; } };
        return (GitRepository) Proxy.newProxyInstance(GitRepository.class.getClassLoader(), new Class<?>[]{GitRepository.class},
                (proxy, method, values) -> switch (method.getName()) {
                    case "getRoot" -> root;
                    case "equals" -> proxy == values[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> null;
                });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
