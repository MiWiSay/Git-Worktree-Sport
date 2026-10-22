package com.miwi.gitworktree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import git4idea.GitBranch;
import git4idea.actions.ref.GitSingleRefAction;
import git4idea.repo.GitRepository;
import com.miwi.gitworktree.service.ModalOperation;
import com.miwi.gitworktree.service.WorktreeService;
import com.miwi.gitworktree.ui.I18n;

import javax.swing.JButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import java.awt.FlowLayout;
import java.awt.BorderLayout;
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
        GitRepository repository = chooseRepository(project, repositories);
        if (repository == null) return;
        openCreateDialog(project, repository, branch);
    }

    /** 分支弹窗与工作区列表入口共用相同的校验、进度及成功反馈流程。 */
    public void openCreateDialog(Project project, GitRepository repository, GitBranch branch) {
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
        String safeBranch = branchSpec.localName().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if (safeBranch.isBlank()) safeBranch = "branch";
        String initialPath = parent.resolve(repositoryRoot.getFileName() + "-" + safeBranch).toString();
        PathDialog dialog = new PathDialog(project, initialPath,
                branchSpec, (selectedPath, spec) -> create(project, repositoryRoot, branchSpec, selectedPath, spec));
        if (dialog.showAndGet()) {
            Path createdPath = Path.of(dialog.path().trim()).toAbsolutePath().normalize();
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
            OpenWorktreeManagerAction.showAndReveal(project, createdPath);
        }
    }

    private boolean create(Project project, Path repositoryRoot, WorktreeService.BranchSpec branchSpec,
                           String selectedPath, WorktreeService.CreateSpec spec) throws Exception {
        Path target;
        try {
            target = Path.of(selectedPath.trim()).toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new IllegalArgumentException(I18n.format("error.invalidPath", e.getMessage()), e);
        }
        WorktreeService.BranchSpec conflictBranch = switch (spec.mode()) {
            case EXISTING_BRANCH -> branchSpec;
            case NEW_BRANCH, ORPHAN -> new WorktreeService.BranchSpec(spec.branchName(), null, false);
            case DETACHED -> new WorktreeService.BranchSpec("(detached HEAD)", null, false);
        };
        WorktreeService.CreateResolution resolution = chooseResolution(project, repositoryRoot, target, conflictBranch,
                spec.mode() == WorktreeService.CreateMode.EXISTING_BRANCH,
                spec.mode() != WorktreeService.CreateMode.DETACHED);
        if (resolution == null) return false;
        ModalOperation.run(project, I18n.text("title.creating"), I18n.text("progress.create"), () -> {
            WorktreeService.create(repositoryRoot, target, branchSpec, resolution, spec);
            return null;
        });
        return true;
    }

    private WorktreeService.CreateResolution chooseResolution(Project project, Path root, Path target,
                                                               WorktreeService.BranchSpec branch, boolean allowForce,
                                                               boolean checkBranchAvailability) {
        try {
            WorktreeService.CreateConflict conflict = WorktreeService.inspectCreate(root, target, branch, checkBranchAvailability);
            return switch (conflict.type()) {
                case NONE -> WorktreeService.CreateResolution.NORMAL;
                case STALE_REGISTRATION -> choose(project,
                        I18n.format("create.staleRegistration", target),
                        I18n.text("title.resolveStale"), I18n.text("button.cleanAndCreate"), I18n.text("button.cancel")) == 0
                        ? WorktreeService.CreateResolution.PRUNE_THEN_CREATE : null;
                case ACTIVE_WORKTREE -> {
                    Messages.showWarningDialog(project,
                            I18n.format("create.activeWorktree", conflict.entry().path()), I18n.text("title.alreadyExists"));
                    yield null;
                }
                case BRANCH_IN_USE -> {
                    if (!allowForce) {
                        Messages.showWarningDialog(project,
                                I18n.format("create.branchInUse", branch.localName(), conflict.entry().path()),
                                I18n.text("title.branchInUse"));
                        yield null;
                    }
                    yield choose(project,
                            I18n.format("create.branchInUse", branch.localName(), conflict.entry().path()),
                            I18n.text("title.branchInUse"), I18n.text("button.forceCreate"), I18n.text("button.cancel")) == 0
                            ? WorktreeService.CreateResolution.FORCE : null;
                }
                case NON_EMPTY_DIRECTORY -> {
                    Messages.showWarningDialog(project,
                            I18n.format("create.nonEmptyDirectory", target), I18n.text("title.nonEmptyDirectory"));
                    yield null;
                }
            };
        } catch (Exception e) {
            Messages.showErrorDialog(project, message(e), I18n.text("title.checkFailed"));
            return null;
        }
    }

    private int choose(Project project, String message, String title, String primary, String cancel) {
        return Messages.showDialog(project, message, title, new String[]{primary, cancel}, 1, Messages.getWarningIcon());
    }

    private GitRepository chooseRepository(Project project, List<? extends GitRepository> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            Messages.showErrorDialog(project, I18n.text("error.noRepository"), I18n.text("title.create"));
            return null;
        }
        if (repositories.size() == 1) return repositories.get(0);
        String[] choices = repositories.stream().map(repository -> repository.getRoot().getPath()).toArray(String[]::new);
        int selected = Messages.showChooseDialog(project, I18n.text("dialog.selectRepository"), I18n.text("title.create"),
                Messages.getQuestionIcon(), choices, choices[0]);
        return selected < 0 ? null : repositories.get(selected);
    }

    private static String message(Exception e) { return e.getMessage() == null ? e.toString() : e.getMessage(); }

    private static final class PathDialog extends DialogWrapper {
        private final JTextField pathField;
        private final JTextField branchNameField;
        private final JTextField startPointField;
        private final List<JRadioButton> modeButtons = new ArrayList<>();
        private final JCheckBox noCheckout;
        private final PathSubmission submission;
        private final Project project;
        private JPanel modeFieldsPanel;

        private PathDialog(Project project, String initialPath, WorktreeService.BranchSpec selectedBranch,
                           PathSubmission submission) {
            super(project);
            this.project = project;
            this.pathField = new JTextField(initialPath, 42);
            this.submission = submission;
            this.branchNameField = new JTextField("new-" + selectedBranch.localName(), 30);
            String defaultStart = selectedBranch.startPoint() == null ? selectedBranch.localName() : selectedBranch.startPoint();
            this.startPointField = new JTextField(defaultStart, 30);
            this.noCheckout = new JCheckBox(I18n.text("create.noCheckout"));
            this.noCheckout.setToolTipText(I18n.text("create.noCheckoutHint"));
            ButtonGroup modeGroup = new ButtonGroup();
            addModeButton(modeGroup, WorktreeService.CreateMode.EXISTING_BRANCH, "create.mode.existing", true);
            addModeButton(modeGroup, WorktreeService.CreateMode.NEW_BRANCH, "create.mode.new", false);
            addModeButton(modeGroup, WorktreeService.CreateMode.DETACHED, "create.mode.detached", false);
            addModeButton(modeGroup, WorktreeService.CreateMode.ORPHAN, "create.mode.orphan", false);
            setTitle(I18n.text("title.create"));
            setOKButtonText(I18n.text("button.create"));
            setCancelButtonText(I18n.text("button.cancel"));
            init();
        }

        String path() { return pathField.getText(); }

        private void addModeButton(ButtonGroup group, WorktreeService.CreateMode mode, String labelKey,
                                   boolean selected) {
            JRadioButton button = new JRadioButton(I18n.text(labelKey), selected);
            button.putClientProperty("mode", mode);
            button.addActionListener(event -> updateModeFields());
            group.add(button);
            modeButtons.add(button);
        }

        private WorktreeService.CreateMode selectedMode() {
            return modeButtons.stream().filter(JRadioButton::isSelected)
                    .map(button -> (WorktreeService.CreateMode) button.getClientProperty("mode"))
                    .findFirst().orElse(WorktreeService.CreateMode.EXISTING_BRANCH);
        }

        private WorktreeService.CreateSpec createSpec() {
            WorktreeService.CreateMode mode = selectedMode();
            return new WorktreeService.CreateSpec(mode, branchNameField.getText().trim(),
                    startPointField.getText().trim(), noCheckout.isSelected());
        }

        private void updateModeFields() {
            WorktreeService.CreateMode mode = selectedMode();
            boolean branchNameVisible = mode == WorktreeService.CreateMode.NEW_BRANCH || mode == WorktreeService.CreateMode.ORPHAN;
            branchNameField.setVisible(branchNameVisible);
            startPointField.setVisible(mode == WorktreeService.CreateMode.NEW_BRANCH || mode == WorktreeService.CreateMode.DETACHED);
            ((JLabel) branchNameField.getClientProperty("label")).setVisible(branchNameVisible);
            ((JLabel) startPointField.getClientProperty("label")).setVisible(startPointField.isVisible());
            boolean orphan = mode == WorktreeService.CreateMode.ORPHAN;
            if (orphan && noCheckout.isSelected()) noCheckout.setSelected(false);
            noCheckout.setEnabled(!orphan);
            noCheckout.setToolTipText(orphan ? I18n.text("create.noCheckoutOrphanHint") : null);
            if (modeFieldsPanel != null) {
                modeFieldsPanel.revalidate();
                modeFieldsPanel.repaint();
            }
        }

        @Override protected void doOKAction() {
            try {
                if (!submission.submit(pathField.getText(), createSpec())) {
                    return;
                }
                super.doOKAction();
            } catch (Exception e) {
                Messages.showErrorDialog(getContentPanel(), message(e), I18n.text("operation.failed"));
            }
        }

        @Override protected JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            JPanel fields = new JPanel(new java.awt.GridBagLayout());
            modeFieldsPanel = fields;
            java.awt.GridBagConstraints constraints = new java.awt.GridBagConstraints();
            constraints.insets = new java.awt.Insets(4, 4, 4, 4);
            constraints.anchor = java.awt.GridBagConstraints.WEST;
            constraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
            constraints.gridx = 0; constraints.gridy = 0;
            fields.add(new JLabel(I18n.text("create.mode")), constraints);
            JPanel modeOptions = new JPanel();
            modeOptions.setLayout(new javax.swing.BoxLayout(modeOptions, javax.swing.BoxLayout.Y_AXIS));
            modeButtons.forEach(modeOptions::add);
            constraints.gridx = 1; constraints.weightx = 1;
            fields.add(modeOptions, constraints);
            JLabel branchNameLabel = new JLabel(I18n.text("create.branchName"));
            JLabel startPointLabel = new JLabel(I18n.text("create.startPoint"));
            branchNameField.putClientProperty("label", branchNameLabel);
            startPointField.putClientProperty("label", startPointLabel);
            constraints.weightx = 0; constraints.gridx = 0; constraints.gridy++;
            fields.add(branchNameLabel, constraints);
            constraints.gridx = 1; constraints.weightx = 1;
            fields.add(branchNameField, constraints);
            constraints.weightx = 0; constraints.gridx = 0; constraints.gridy++;
            fields.add(startPointLabel, constraints);
            constraints.gridx = 1; constraints.weightx = 1;
            fields.add(startPointField, constraints);
            constraints.gridx = 0; constraints.gridy++; constraints.gridwidth = 2;
            fields.add(noCheckout, constraints);
            // 动态字段显隐时，只让底部留白吸收多余高度，避免选项组随模式切换上下漂移。
            constraints.gridy++; constraints.weighty = 1; constraints.fill = java.awt.GridBagConstraints.VERTICAL;
            fields.add(new JPanel(), constraints);
            JPanel pathRow = new JPanel(new BorderLayout(6, 0));
            pathRow.add(pathField, BorderLayout.CENTER);
            JButton browse = new JButton(I18n.text("action.browse"));
            browse.addActionListener(event -> {
                VirtualFile selected = FileChooser.chooseFile(
                        FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null);
                if (selected != null) pathField.setText(selected.getPath());
            });
            pathRow.add(browse, BorderLayout.EAST);
            JPanel content = new JPanel(new BorderLayout(6, 6));
            content.add(new JLabel(I18n.text("dialog.create")), BorderLayout.NORTH);
            content.add(fields, BorderLayout.CENTER);
            content.add(pathRow, BorderLayout.SOUTH);
            panel.add(content, BorderLayout.CENTER);
            updateModeFields();
            return panel;
        }
    }

    @FunctionalInterface
    private interface PathSubmission {
        boolean submit(String path, WorktreeService.CreateSpec spec) throws Exception;
    }

}
