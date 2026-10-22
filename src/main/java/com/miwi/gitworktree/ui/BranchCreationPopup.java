package com.miwi.gitworktree.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.awt.RelativePoint;
import com.miwi.gitworktree.actions.CreateWorktreeFromBranchAction;
import git4idea.GitBranch;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

import javax.swing.Icon;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 使用 IDE 原生可滚动分级弹窗，不为每个分支复制创建业务逻辑。 */
final class BranchCreationPopup {
    private BranchCreationPopup() {}

    static void show(Project owner, MouseEvent event) {
        if (owner.isDisposed()) return;
        Map<String, GitRepository> repositories = new LinkedHashMap<>();
        List<Project> projects = new ArrayList<>();
        projects.add(owner);
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (project != owner) projects.add(project);
        }
        for (Project project : projects) {
            if (project.isDisposed()) continue;
            for (GitRepository repository : GitRepositoryManager.getInstance(project).getRepositories()) {
                repositories.putIfAbsent(repository.getRoot().getPath(), repository);
            }
        }

        List<BranchChoice> choices = new ArrayList<>();
        repositories.values().stream().sorted(Comparator.comparing(repository -> repository.getRoot().getPath(),
                String.CASE_INSENSITIVE_ORDER)).forEach(repository -> {
            List<GitBranch> branches = new ArrayList<>(repository.getBranches().getLocalBranches());
            branches.addAll(repository.getBranches().getRemoteBranches());
            branches.sort(Comparator.comparing(GitBranch::isRemote)
                    .thenComparing(GitBranch::getName, String.CASE_INSENSITIVE_ORDER));
            branches.forEach(branch -> choices.add(new BranchChoice(repository, branch)));
        });
        if (choices.isEmpty()) {
            JBPopupFactory.getInstance().createMessage(I18n.text(repositories.isEmpty()
                    ? "tree.noRepositories" : "tree.noBranches")).show(new RelativePoint(event));
            return;
        }
        ListPopup popup = JBPopupFactory.getInstance().createListPopup(new BranchStep(owner, choices), 18);
        popup.setShowSubmenuOnHover(false);
        popup.setHandleAutoSelectionBeforeShow(false);
        popup.show(new RelativePoint(event));
    }

    record BranchChoice(GitRepository repository, GitBranch branch) {}

    private static final class BranchStep extends BaseListPopupStep<BranchChoice> {
        private final Project owner;
        private final List<BranchChoice> choices;

        private BranchStep(Project owner, List<BranchChoice> choices) {
            super(I18n.text("tree.branches"), choices);
            this.owner = owner;
            this.choices = choices;
        }

        @Override public String getTextFor(BranchChoice choice) { return choice.branch().getName(); }
        @Override public String getIndexedString(BranchChoice choice) {
            return choice.repository().getRoot().getPath() + " " + getTextFor(choice);
        }
        @Override public Icon getIconFor(BranchChoice choice) { return AllIcons.Vcs.Branch; }
        @Override public boolean isSpeedSearchEnabled() { return true; }
        @Override public boolean isAutoSelectionEnabled() { return false; }
        @Override public boolean hasSubstep(BranchChoice choice) { return true; }

        @Override public ListSeparator getSeparatorAbove(BranchChoice choice) {
            int index = choices.indexOf(choice);
            BranchChoice previous = index > 0 ? choices.get(index - 1) : null;
            if (previous != null && previous.repository() == choice.repository()
                    && previous.branch().isRemote() == choice.branch().isRemote()) return null;
            String kind = I18n.text(choice.branch().isRemote() ? "tree.remoteBranches" : "tree.localBranches");
            return new ListSeparator(choice.repository().getRoot().getPath() + " — " + kind);
        }

        @Override public PopupStep<?> onChosen(BranchChoice choice, boolean finalChoice) {
            // 即使鼠标单击或回车，也先展示创建子菜单，避免选择分支就立即弹出对话框。
            return new BaseListPopupStep<String>(null, List.of(I18n.text("branch.create"))) {
                @Override public boolean isAutoSelectionEnabled() { return false; }
                @Override public PopupStep<?> onChosen(String value, boolean finalChoice) {
                    return doFinalStep(() -> {
                        if (!owner.isDisposed()) new CreateWorktreeFromBranchAction()
                                .openCreateDialog(owner, choice.repository(), choice.branch());
                    });
                }
            };
        }
    }
}
