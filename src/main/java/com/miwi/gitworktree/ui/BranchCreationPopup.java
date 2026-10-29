package com.miwi.gitworktree.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.SpeedSearchFilter;
import com.intellij.openapi.ui.popup.MnemonicNavigationFilter;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.CaptionPanel;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import com.miwi.gitworktree.actions.CreateWorktreeFromBranchAction;
import git4idea.GitBranch;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** 使用 IDE 原生可滚动分级弹窗，不为每个分支复制创建业务逻辑。 */
final class BranchCreationPopup {
    private BranchCreationPopup() {}

    static void show(Project owner, MouseEvent event) {
        if (owner.isDisposed()) return;
        show(owner, new RelativePoint(event), loadChoices(owner), hasRepositories(owner));
    }

    private static void show(Project owner, RelativePoint point, List<BranchChoice> choices, boolean hasRepositories) {
        if (owner.isDisposed()) return;
        if (choices.isEmpty()) {
            JBPopupFactory.getInstance().createMessage(I18n.text(hasRepositories
                    ? "tree.noBranches" : "tree.noRepositories")).show(point);
            return;
        }
        BranchStep step = new BranchStep(owner, choices);
        ListPopup popup = JBPopupFactory.getInstance().createListPopup(step, 18);
        popup.setShowSubmenuOnHover(false);
        popup.setHandleAutoSelectionBeforeShow(false);
        installRefreshButton(popup, step, owner, point);
        popup.show(point);
    }

    private static List<BranchChoice> loadChoices(Project owner) {
        Map<String, GitRepository> repositories = repositories(owner);
        List<BranchChoice> choices = new ArrayList<>();
        repositories.values().stream().sorted(Comparator.comparing(repository -> repository.getRoot().getPath(),
                String.CASE_INSENSITIVE_ORDER)).forEach(repository -> {
            List<GitBranch> branches = new ArrayList<>(repository.getBranches().getLocalBranches());
            branches.addAll(repository.getBranches().getRemoteBranches());
            branches.sort(Comparator.comparing(GitBranch::isRemote)
                    .thenComparing(GitBranch::getName, String.CASE_INSENSITIVE_ORDER));
            branches.forEach(branch -> choices.add(new BranchChoice(repository, branch)));
        });
        return choices;
    }

    private static Map<String, GitRepository> repositories(Project owner) {
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

        return repositories;
    }

    private static boolean hasRepositories(Project owner) {
        return !repositories(owner).isEmpty();
    }

    private static void installRefreshButton(ListPopup popup, BranchStep step, Project owner, RelativePoint point) {
        Runnable install = () -> {
            CaptionPanel caption = findCaptionPanel(popup.getContent());
            if (caption == null || Boolean.TRUE.equals(caption.getClientProperty(BranchCreationPopup.class))) return;
            caption.putClientProperty(BranchCreationPopup.class, Boolean.TRUE);
            Icon icon = new ScaledIcon(IconLoader.getIcon("/icons/refresh.svg", BranchCreationPopup.class), 14);
            final ToolbarIconButton[] buttonRef = new ToolbarIconButton[1];
            ToolbarIconButton button = new ToolbarIconButton(I18n.text("action.refresh"), icon, ignored -> {
                buttonRef[0].setBusy(true, AnimatedIcon.Default.INSTANCE);
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    List<BranchChoice> refreshedChoices = null;
                    boolean refreshedHasRepositories = false;
                    Throwable failure = null;
                    try {
                        for (GitRepository repository : repositories(owner).values()) {
                            if (!repository.isDisposed()) repository.update();
                        }
                        refreshedChoices = loadChoices(owner);
                        refreshedHasRepositories = hasRepositories(owner);
                    } catch (Throwable error) {
                        failure = error;
                    }
                    List<BranchChoice> result = refreshedChoices;
                    boolean hasRepos = refreshedHasRepositories;
                    Throwable refreshFailure = failure;
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (owner.isDisposed()) return;
                        if (refreshFailure != null) {
                            buttonRef[0].setBusy(false, AnimatedIcon.Default.INSTANCE);
                            Messages.showErrorDialog(owner,
                                    refreshFailure.getMessage() == null ? refreshFailure.toString() : refreshFailure.getMessage(),
                                    I18n.text("title.checkFailed"));
                            return;
                        }
                        if (result.isEmpty()) {
                            popup.cancel();
                            show(owner, point, result, hasRepos);
                        } else {
                            step.replaceChoices(result);
                            buttonRef[0].setBusy(false, AnimatedIcon.Default.INSTANCE);
                        }
                    });
                });
            });
            buttonRef[0] = button;
            caption.setButtonComponent(button, JBUI.Borders.emptyRight(4));
        };
        // createListPopup usually has its header ready immediately, but keep the listener
        // as a compatibility fallback for IDE builds that finish popup layout lazily.
        install.run();
        popup.addListener(new JBPopupListener() {
            @Override public void beforeShown(com.intellij.openapi.ui.popup.LightweightWindowEvent event) { install.run(); }
        });
    }

    private static CaptionPanel findCaptionPanel(Component component) {
        if (component instanceof CaptionPanel caption) return caption;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                CaptionPanel result = findCaptionPanel(child);
                if (result != null) return result;
            }
        }
        return null;
    }

    record BranchChoice(GitRepository repository, GitBranch branch) {}

    private static final class BranchStep implements ListPopupStep<BranchChoice>, SpeedSearchFilter<BranchChoice>,
            MnemonicNavigationFilter<BranchChoice> {
        private final Project owner;
        private List<BranchChoice> choices;
        private final List<ListPopupModelListener> listeners = new CopyOnWriteArrayList<>();

        private BranchStep(Project owner, List<BranchChoice> choices) {
            this.owner = owner;
            this.choices = List.copyOf(choices);
        }

        private void replaceChoices(List<BranchChoice> updated) {
            choices = List.copyOf(updated);
            listeners.forEach(ListPopupModelListener::onModelChanged);
        }

        @Override public String getTitle() { return I18n.text("tree.branches"); }
        @Override public List<BranchChoice> getValues() { return choices; }
        @Override public boolean isSelectable(BranchChoice choice) { return true; }
        @Override public javax.swing.Icon getIconFor(BranchChoice choice) { return AllIcons.Vcs.Branch; }
        @Override public String getTextFor(BranchChoice choice) { return choice.branch().getName(); }
        @Override public ListSeparator getSeparatorAbove(BranchChoice choice) {
            int index = choices.indexOf(choice);
            BranchChoice previous = index > 0 ? choices.get(index - 1) : null;
            if (previous != null && previous.repository() == choice.repository()
                    && previous.branch().isRemote() == choice.branch().isRemote()) return null;
            String kind = I18n.text(choice.branch().isRemote() ? "tree.remoteBranches" : "tree.localBranches");
            return new ListSeparator(choice.repository().getRoot().getPath() + " — " + kind);
        }
        @Override public int getDefaultOptionIndex() { return -1; }
        @Override public boolean isSpeedSearchEnabled() { return true; }
        @Override public SpeedSearchFilter<BranchChoice> getSpeedSearchFilter() { return this; }
        @Override public String getIndexedString(BranchChoice choice) {
            return choice.repository().getRoot().getPath() + " " + getTextFor(choice);
        }
        @Override public boolean isAutoSelectionEnabled() { return false; }
        @Override public boolean hasSubstep(BranchChoice choice) { return true; }
        @Override public Runnable getFinalRunnable() { return null; }
        @Override public boolean isMnemonicsNavigationEnabled() { return false; }
        @Override public MnemonicNavigationFilter<BranchChoice> getMnemonicNavigationFilter() { return this; }
        @Override public int getMnemonicPos(BranchChoice choice) { return -1; }
        @Override public void canceled() {}
        @Override public void addListener(ListPopupModelListener listener) { listeners.add(listener); }
        @Override public void removeListener(ListPopupModelListener listener) { listeners.remove(listener); }
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
