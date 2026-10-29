package com.miwi.gitworktree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import git4idea.GitBranch;
import git4idea.actions.ref.GitSingleRefAction;
import git4idea.repo.GitRepository;
import com.miwi.gitworktree.service.ModalOperation;
import com.miwi.gitworktree.service.WorktreeService;
import com.miwi.gitworktree.ui.I18n;

import com.miwi.gitworktree.ui.CreateWorktreeForm;
import javax.swing.JComponent;
import javax.swing.DefaultListCellRenderer;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CreateWorktreeFromBranchAction extends GitSingleRefAction<GitBranch> {
    public CreateWorktreeFromBranchAction() {
        super(() -> I18n.text("branch.create"));
    }

    @Override protected Class<GitBranch> getRefClass() { return GitBranch.class; }


    @Override
    public void actionPerformed(AnActionEvent event, Project project, List<? extends GitRepository> repositories, GitBranch branch) {
        if (repositories == null || repositories.isEmpty()) {
            Messages.showErrorDialog(project, I18n.text("error.noRepository"), I18n.text("title.create"));
            return;
        }
        if (repositories.size() == 1) {
            openCreateDialog(project, repositories.get(0), branch);
            return;
        }
        List<GitRepository> repositoryChoices = new ArrayList<>(repositories);
        JBPopupFactory.getInstance().createPopupChooserBuilder(repositoryChoices)
                .setTitle(I18n.text("dialog.selectRepository"))
                .setRenderer(new DefaultListCellRenderer() {
                    @Override
                    public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                            int index, boolean isSelected, boolean cellHasFocus) {
                        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                        if (value instanceof GitRepository repository) {
                            setText(repository.getRoot().getPath());
                        }
                        return this;
                    }
                })
                .setSelectedValue(repositories.get(0), true)
                .setItemChosenCallback(repository -> openCreateDialog(project, repository, branch))
                .createPopup()
                .showCenteredInCurrentWindow(project);
    }

    /** 分支弹窗与工作区列表入口共用相同的校验、进度及成功反馈流程。 */
    public void openCreateDialog(Project project, GitRepository repository, GitBranch branch) {
        openCreateDialog(project, repository, branch, false);
    }

    /**
     * 工作区条目的派生创建入口默认新建分支；不展示检出现有分支或没有继承关系的孤儿模式。
     */
    public void openCreateDialog(Project project, GitRepository repository, GitBranch branch,
                                 boolean createNewBranchFromSelected) {
        if (project.isDisposed()) return;

        WorktreeService.BranchSpec branchSpec;
        try {
            branchSpec = WorktreeService.resolveBranch(branch, repository);
        } catch (Exception e) {
            Messages.showErrorDialog(project, message(e), I18n.text("title.create"));
            return;
        }

        Path repositoryRoot = Path.of(repository.getRoot().getPath());
        Path parent = repositoryRoot.getParent();
        if (parent == null) {
            Messages.showErrorDialog(project, I18n.text("error.repositoryParent"), I18n.text("title.create"));
            return;
        }
        List<WorktreeService.BranchReference> branches;
        try {
            branches = ModalOperation.run(project, I18n.text("title.create"), I18n.text("progress.create"),
                    () -> WorktreeService.creationBranches(repositoryRoot));
        } catch (Exception e) {
            Messages.showErrorDialog(project, message(e), I18n.text("title.checkFailed"));
            return;
        }
        PathDialog dialog = new PathDialog(project, repositoryRoot, branchSpec, branches,
                createNewBranchFromSelected,
                (selectedPath, spec) -> create(project, repositoryRoot, branchSpec, selectedPath, spec));
        if (dialog.showAndGet()) {
            Path createdPath = Path.of(dialog.path().trim()).toAbsolutePath().normalize();
            // The creation dialog has already closed. Refresh and select the new entry
            // immediately so the list is current even while the success prompt is shown.
            OpenWorktreeManagerAction.showAndReveal(project, createdPath);
            int answer = Messages.showDialog(project, I18n.format("create.success", createdPath),
                    I18n.text("title.created"),
                    new String[]{I18n.text("button.openProject"), I18n.text("button.close")},
                    1, Messages.getInformationIcon());
            if (answer == 0) {
                try {
                    // 不强制新窗口：交给 IDE 的打开工程流程决定窗口、信任和导入行为。
                    if (ProjectUtil.openOrImport(createdPath, project, false) != null) return;
                } catch (ProcessCanceledException ignored) {
                    // 取消打开不撤销已创建的工作树，仍在原窗口定位它。
                } catch (Exception e) {
                    if (!project.isDisposed()) {
                        Messages.showErrorDialog(project,
                                I18n.format("error.openProject", createdPath, message(e)),
                                I18n.text("button.openProject"));
                    }
                }
            }
        }
    }

    private boolean create(Project project, Path repositoryRoot, WorktreeService.BranchSpec branchSpec,
                           String selectedPath, WorktreeService.CreateSpec spec) throws Exception {
        WorktreeService.BranchSpec conflictBranch = switch (spec.mode()) {
            case EXISTING_BRANCH -> branchSpec;
            case NEW_BRANCH, ORPHAN -> new WorktreeService.BranchSpec(spec.branchName(), null, false);
            case DETACHED -> new WorktreeService.BranchSpec("(detached HEAD)", null, false);
        };
        // Start the modal progress task from this UI action. It runs Git work in the
        // background while keeping the creation form open and disabled underneath.
        // Preflight is included so a slow ref check cannot look like a frozen button.
        CreationAttempt attempt = ModalOperation.runWithWaitingFeedback(project,
                I18n.text("title.creating"), I18n.text("progress.create"), I18n.text("progress.create"),
                () -> inspectAndCreate(repositoryRoot, selectedPath, branchSpec, conflictBranch, spec));
        if (!attempt.issues().isEmpty()) throw new InputConflictException(attempt.issues());
        if (attempt.created()) return true;

        Path target = Path.of(selectedPath.trim()).toAbsolutePath().normalize();
        WorktreeService.CreateConflict conflict = attempt.conflict();
        boolean allowForce = spec.mode() == WorktreeService.CreateMode.EXISTING_BRANCH;
        return switch (conflict.type()) {
            case NONE -> throw new IllegalStateException("Creation attempt returned no result.");
            case STALE_REGISTRATION -> {
                String details = attempt.detail().isBlank() ? I18n.text("prune.none") : attempt.detail();
                String message = I18n.format("create.staleRegistration", target) + "\n\n" + details;
                if (choose(project, message,
                        I18n.text("title.resolveStale"), I18n.text("button.cleanAndCreate"),
                        I18n.text("button.cancel")) != 0) yield false;
                runCreate(project, repositoryRoot, target, branchSpec,
                        WorktreeService.CreateResolution.PRUNE_THEN_CREATE, spec);
                yield true;
            }
            case ACTIVE_WORKTREE -> {
                Messages.showWarningDialog(project,
                        I18n.format("create.activeWorktree", conflict.entry().path()),
                        I18n.text("title.alreadyExists"));
                yield false;
            }
            case BRANCH_IN_USE -> {
                String message = I18n.format("create.branchInUse", conflictBranch.localName(), conflict.entry().path());
                if (!allowForce) {
                    Messages.showWarningDialog(project, message, I18n.text("title.branchInUse"));
                    yield false;
                }
                if (choose(project, message,
                        I18n.text("title.branchInUse"), I18n.text("button.forceCreate"),
                        I18n.text("button.cancel")) != 0) yield false;
                runCreate(project, repositoryRoot, target, branchSpec,
                        WorktreeService.CreateResolution.FORCE, spec);
                yield true;
            }
            case NON_EMPTY_DIRECTORY -> {
                Messages.showWarningDialog(project,
                        I18n.format("create.nonEmptyDirectory", target), I18n.text("title.nonEmptyDirectory"));
                yield false;
            }
        };
    }

    private CreationAttempt inspectAndCreate(Path repositoryRoot, String selectedPath,
                                             WorktreeService.BranchSpec branchSpec,
                                             WorktreeService.BranchSpec conflictBranch,
                                             WorktreeService.CreateSpec spec) throws Exception {
        List<WorktreeService.CreateIssue> issues =
                WorktreeService.validateCreateInputs(repositoryRoot, selectedPath, spec);
        if (!issues.isEmpty()) return new CreationAttempt(false, issues, null, "");

        Path target = Path.of(selectedPath.trim()).toAbsolutePath().normalize();
        WorktreeService.CreateConflict conflict = WorktreeService.inspectCreate(repositoryRoot, target,
                conflictBranch, spec.mode() != WorktreeService.CreateMode.DETACHED);
        if (conflict.type() == WorktreeService.CreateConflictType.NONE) {
            WorktreeService.create(repositoryRoot, target, branchSpec,
                    WorktreeService.CreateResolution.NORMAL, spec);
            return new CreationAttempt(true, List.of(), conflict, "");
        }
        String detail = conflict.type() == WorktreeService.CreateConflictType.STALE_REGISTRATION
                ? WorktreeService.prunePreview(repositoryRoot) : "";
        return new CreationAttempt(false, List.of(), conflict, detail);
    }

    private void runCreate(Project project, Path repositoryRoot, Path target,
                           WorktreeService.BranchSpec branchSpec,
                           WorktreeService.CreateResolution resolution,
                           WorktreeService.CreateSpec spec) throws Exception {
        ModalOperation.runWithWaitingFeedback(project,
                I18n.text("title.creating"), I18n.text("progress.create"), I18n.text("progress.create"),
                () -> {
                    WorktreeService.create(repositoryRoot, target, branchSpec, resolution, spec);
                    return null;
                });
    }

    private int choose(Project project, String message, String title, String primary, String cancel) {
        return Messages.showDialog(project, message, title, new String[]{primary, cancel}, 1, Messages.getWarningIcon());
    }

    private static String message(Exception e) { return e.getMessage() == null ? e.toString() : e.getMessage(); }

    private static String message(Throwable e) { return e.getMessage() == null ? e.toString() : e.getMessage(); }

    private static final class InputConflictException extends Exception {
        private final List<WorktreeService.CreateIssue> issues;

        private InputConflictException(List<WorktreeService.CreateIssue> issues) {
            super(issues.stream().map(WorktreeService.CreateIssue::message)
                    .collect(java.util.stream.Collectors.joining("\n\n")));
            this.issues = List.copyOf(issues);
        }
    }

    private record CreationAttempt(boolean created, List<WorktreeService.CreateIssue> issues,
                                   WorktreeService.CreateConflict conflict, String detail) {}

    private static final class PathDialog extends DialogWrapper {
        private final CreateWorktreeForm form;
        private final PathSubmission submission;
        private boolean submitting;

        private PathDialog(Project project, Path root, WorktreeService.BranchSpec selectedBranch,
                           List<WorktreeService.BranchReference> branches, boolean derived,
                           PathSubmission submission) {
            super(project);
            this.submission = submission;
            form = new CreateWorktreeForm(root, selectedBranch, branches, derived, accept -> {
                VirtualFile selected = FileChooser.chooseFile(
                        FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null);
                if (selected != null) accept.accept(selected.getPath());
            });
            setTitle(I18n.text("title.create"));
            setOKButtonText(I18n.text("button.create"));
            setCancelButtonText(I18n.text("button.cancel"));
            form.setLayoutChangeListener(this::repackForCurrentMode);
            init();
        }

        private void repackForCurrentMode() {
            if (!isShowing()) return;
            SwingUtilities.invokeLater(() -> {
                if (!isShowing()) return;
                // Keep the width stable while allowing the height to follow the
                // currently visible mode. This removes the unused area left by
                // the largest mode without making the dialog jump horizontally.
                Dimension current = getSize();
                pack();
                if (getSize().width < current.width) setSize(current.width, getSize().height);
            });
        }

        String path() { return form.path(); }

        @Override protected void doOKAction() {
            if (submitting) return;
            submitting = true;
            form.setBusy(true);
            getOKAction().setEnabled(false);
            getCancelAction().setEnabled(false);
            setOKButtonIcon(AnimatedIcon.Default.INSTANCE);
            List<WorktreeService.CreateIssue> required = form.requiredIssues();
            if (!required.isEmpty()) {
                restoreSubmitControls();
                form.markIssues(required);
                Messages.showWarningDialog(getContentPanel(), required.stream()
                        .map(WorktreeService.CreateIssue::message)
                        .collect(java.util.stream.Collectors.joining("\n\n")), I18n.text("operation.failed"));
                return;
            }
            String selectedPath = form.path();
            WorktreeService.CreateSpec selectedSpec = form.spec();
            try {
                if (submission.submit(selectedPath, selectedSpec)) {
                    // The wait dialog has closed. Close the form only after Git returns;
                    // its caller then refreshes the list and reveals the new entry.
                    // DialogWrapper.doOKAction() returns early while OK is disabled for
                    // the busy state; re-enable it immediately before the standard close.
                    getOKAction().setEnabled(true);
                    super.doOKAction();
                    return;
                }
                restoreSubmitControls();
            } catch (InputConflictException e) {
                restoreSubmitControls();
                form.markIssues(e.issues);
                Messages.showWarningDialog(getContentPanel(), e.getMessage(), I18n.text("operation.failed"));
            } catch (Exception e) {
                restoreSubmitControls();
                Messages.showErrorDialog(getContentPanel(), message(e), I18n.text("operation.failed"));
            }
        }

        @Override public void doCancelAction() {
            // Git worktree add may already have created the checkout; closing mid-operation
            // would hide whether the worktree was registered successfully.
            if (submitting) return;
            super.doCancelAction();
        }

        private void restoreSubmitControls() {
            submitting = false;
            form.setBusy(false);
            setOKButtonIcon(null);
            getOKAction().setEnabled(true);
            getCancelAction().setEnabled(true);
        }

        @Override protected JComponent createCenterPanel() { return form; }
    }

    @FunctionalInterface
    private interface PathSubmission {
        boolean submit(String path, WorktreeService.CreateSpec spec) throws Exception;
    }

}
