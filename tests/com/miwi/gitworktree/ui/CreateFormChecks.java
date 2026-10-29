package com.miwi.gitworktree.ui;

import com.miwi.gitworktree.service.WorktreeService;
import com.miwi.gitworktree.service.WorktreeService.BranchReference;
import com.miwi.gitworktree.service.WorktreeService.CreateMode;
import com.miwi.gitworktree.service.WorktreeService.CreateField;
import com.miwi.gitworktree.service.WorktreeService.CreateIssue;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Exercises the production creation form on the EDT without DialogWrapper or Git mutations. */
public final class CreateFormChecks {
    private static int assertions;
    private static final Path ROOT = Path.of("build/create-form-fixture/repository").toAbsolutePath();
    private static Consumer<String> acceptBrowse;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { run(); } catch (Exception e) { throw new RuntimeException(e); }
        });
        System.out.println("PASS: " + assertions + " production creation-form assertions");
    }

    @SuppressWarnings("unchecked")
    private static void run() throws Exception {
        I18n.setLanguage("zh-CN");
        String originalPrefix = WorktreePreferences.parallelBranchPrefix();
        WorktreePreferences.setParallelBranchPrefix("sync-git");
        var refs = List.of(new BranchReference("refs/heads/main", "main", false),
                new BranchReference("refs/remotes/origin/main", "origin/main", true));
        var selected = new WorktreeService.BranchSpec("main", null, false);
        CreateWorktreeForm form = new CreateWorktreeForm(ROOT, selected, refs, true, accept -> acceptBrowse = accept);
        JTextField name = field(form, "branchNameField", JTextField.class);
        JTextField path = field(form, "pathField", JTextField.class);
        JTextField custom = field(form, "startReference", JTextField.class);
        JCheckBox advanced = field(form, "customReference", JCheckBox.class);
        JComboBox<BranchReference> branches = field(form, "startBranch", JComboBox.class);
        List<JRadioButton> modes = (List<JRadioButton>) field(form, "modeButtons", List.class);
        check(modes.size() == 2, "Derived entry has only new and detached modes");
        check(form.mode() == CreateMode.NEW_BRANCH, "Derived entry defaults to new branch");
        check(modes.stream().noneMatch(b -> b.getClientProperty("mode") == CreateMode.EXISTING_BRANCH
                || b.getClientProperty("mode") == CreateMode.ORPHAN), "Derived shortcut omits unrelated modes");
        check(form.spec().startPoint().equals("refs/heads/main"), "Selected row is the actual full start ref");
        check(!branches.isEditable(), "Normal start ref is a non-editable combo");
        check(branches.getItemCount() == 2, "Selected branch not duplicated among choices");
        List<Component> interactiveControls = interactiveControls(form);
        List<Boolean> enabledBeforeSubmit = interactiveControls.stream().map(Component::isEnabled).toList();
        form.setBusy(true);
        check(interactiveControls.stream().noneMatch(Component::isEnabled),
                "All editable fields, selectors and form buttons are disabled while creating");
        form.setBusy(false);
        check(interactiveControls.size() == enabledBeforeSubmit.size()
                        && java.util.stream.IntStream.range(0, interactiveControls.size())
                        .allMatch(index -> interactiveControls.get(index).isEnabled() == enabledBeforeSubmit.get(index)),
                "Form controls restore their exact enabled states after submission stops");
        check(path.getText().endsWith("repository-new-main"), "Initial suggestion matches default new name");
        name.setText("feature/business-card");
        check(path.getText().endsWith("repository-feature-business-card"), "Branch edits synchronize folder name");
        String customPath = ROOT.getParent().resolve("my-custom-folder").toString();
        path.setText(customPath);
        name.setText("different");
        check(path.getText().equals(customPath), "Manual path is never overwritten");
        button(form, I18n.text("create.resetPath")).doClick();
        check(path.getText().endsWith("repository-different"), "Reset restores automatic mode");
        button(form, I18n.text("action.browse")).doClick();
        acceptBrowse.accept(customPath);
        name.setText("after-browse");
        check(path.getText().equals(customPath), "Browse selection is retained across branch edits");
        button(form, I18n.text("create.resetPath")).doClick();

        Border normalName = name.getBorder();
        Border normalPath = path.getBorder();
        Insets padding = normalName.getBorderInsets(name);
        form.markIssues(List.of(new CreateIssue(CreateField.PATH, "path"), new CreateIssue(CreateField.BRANCH, "name")));
        check(name.getBorder() != normalName && path.getBorder() != normalPath, "Both invalid fields marked together");
        check(name.getBorder().getBorderInsets(name).equals(padding), "Error border preserves IDE padding");
        path.setText(customPath);
        check(path.getBorder() == normalPath, "Editing path clears its error");
        check(name.getBorder() != normalName, "Editing path does not clear unchanged branch error");
        name.setText("edited");
        check(name.getBorder() == normalName, "Editing branch clears its error");
        branches.setSelectedIndex(1);
        check(form.spec().startPoint().equals("refs/remotes/origin/main"), "Remote uses full ref not display label");
        advanced.doClick();
        check(custom.isVisible() && !branches.isVisible(), "Advanced Git refs require explicit opt-in");
        custom.setText("HEAD~1");
        check(form.spec().startPoint().equals("HEAD~1"), "Advanced start point preserves Git revision syntax");
        form.markIssues(List.of(new CreateIssue(CreateField.START_POINT, "invalid")));
        Border errorRef = custom.getBorder();
        custom.setText("HEAD");
        check(custom.getBorder() != errorRef, "Editing advanced ref clears error");
        custom.setText("");
        check(form.requiredIssues().stream().anyMatch(i -> i.field() == CreateField.START_POINT), "Empty ref flagged");
        advanced.doClick();
        check(form.requiredIssues().isEmpty(), "Returning to selected branch ignores empty advanced ref");

        form.setSize(form.getPreferredSize());
        layout(form);
        int y = SwingUtilities.convertPoint(modes.getFirst(), 0, 0, form).y;
        for (JRadioButton mode : modes) {
            check(SwingUtilities.convertPoint(mode, 0, 0, form).y == y, "Radio modes share one horizontal row");
            mode.doClick();
            layout(form);
            check(SwingUtilities.convertPoint(mode, 0, 0, form).y == y, "Switching modes does not move option row");
        }
        check(form.mode() == CreateMode.DETACHED && !name.isVisible(), "Detached does not request a new branch name");
        check(form.path().endsWith("repository-detached") || form.path().equals(customPath), "Detached naming or manual path retained");

        CreateWorktreeForm general = new CreateWorktreeForm(ROOT, selected, refs, false, accept -> {});
        var generalModes = (List<JRadioButton>) field(general, "modeButtons", List.class);
        check(generalModes.size() == 4 && general.mode() == CreateMode.EXISTING_BRANCH, "General entry keeps all modes");
        JCheckBox noCheckout = field(general, "noCheckout", JCheckBox.class);
        noCheckout.setSelected(true);
        generalModes.getLast().doClick();
        check(general.mode() == CreateMode.ORPHAN && !noCheckout.isEnabled() && !noCheckout.isSelected(),
                "Orphan still rejects incompatible no-checkout");
        general.setBusy(true);
        general.setBusy(false);
        check(!noCheckout.isEnabled(), "Busy-state restore preserves controls already disabled by the current mode");
        generalModes.get(1).doClick();
        check(noCheckout.isEnabled(), "Leaving orphan re-enables advanced checkout option");
        general.setSize(general.getPreferredSize());
        layout(general);
        JTextField generalName = field(general, "branchNameField", JTextField.class);
        JComboBox<?> generalStart = field(general, "startBranch", JComboBox.class);
        check(generalName.getHeight() >= generalName.getPreferredSize().height,
                "Existing-to-new mode switch retains enough height for branch editor");
        check(generalStart.getHeight() >= generalStart.getPreferredSize().height,
                "Existing-to-new mode switch retains enough height for source combo");
        JCheckBox parallelMode = field(general, "parallelMode", JCheckBox.class);
        JLabel prefix = field(general, "parallelBranchPrefix", JLabel.class);
        generalModes.get(1).doClick();
        check(parallelMode.isVisible() && !prefix.isVisible(),
                "Parallel mode is offered for new branches without occupying the normal name field");
        generalName.setText("feature/tooling");
        parallelMode.doClick();
        check(prefix.isVisible() && prefix.getText().equals("sync-git/"),
                "Parallel prefix is displayed separately from the editable branch suffix");
        check(generalName.getText().equals("feature/tooling"),
                "Enabling parallel mode does not make the prefix editable as part of the branch name");
        check(formattedBranch(general).equals("sync-git/feature/tooling"),
                "Parallel mode applies the configured namespace to a newly created branch");
        check(general.path().endsWith("repository-sync-git-feature-tooling"),
                "Automatic destination path follows the prefixed branch name");
        parallelMode.doClick();
        check(formattedBranch(general).equals("feature/tooling"),
                "Disabling parallel mode removes only the generated prefix");
        generalModes.get(0).doClick();
        check(!parallelMode.isVisible(), "Parallel mode is hidden for checkout of an existing branch");
        generalModes.get(1).doClick();
        check(!parallelMode.isSelected(), "Switching creation mode clears the hidden parallel option");
        field(general, "branchNameField", JTextField.class).setText("");
        check(general.requiredIssues().stream().anyMatch(i -> i.field() == CreateField.BRANCH), "Empty new name flagged");
        CreateWorktreeForm remote = new CreateWorktreeForm(ROOT,
                new WorktreeService.BranchSpec("main", "refs/remotes/origin/main", true), refs, true, accept -> {});
        check(remote.spec().startPoint().equals("refs/remotes/origin/main"), "Remote invoking branch stays selected");
        for (var language : I18n.languageOptions()) {
            if (language.followsIde()) continue;
            I18n.setLanguage(language.id());
            CreateWorktreeForm localized = new CreateWorktreeForm(ROOT, selected, refs, false, accept -> {});
            localized.setSize(localized.getPreferredSize());
            layout(localized);
            var localizedModes = (List<JRadioButton>) field(localized, "modeButtons", List.class);
            int rowY = SwingUtilities.convertPoint(localizedModes.getFirst(), 0, 0, localized).y;
            for (JRadioButton mode : localizedModes) {
                check(SwingUtilities.convertPoint(mode, 0, 0, localized).y == rowY,
                        "All modes horizontal in " + language.id());
                check(mode.getWidth() >= mode.getPreferredSize().width,
                        "Translated radio text not clipped in " + language.id());
            }
        }
        WorktreePreferences.setParallelBranchPrefix(originalPrefix);
        I18n.setLanguage("en");
    }

    private static String formattedBranch(CreateWorktreeForm form) { return form.spec().branchName(); }

    private static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(owner));
    }
    private static JButton button(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton button && button.getText().equals(text)) return button;
            if (child instanceof Container container) {
                JButton found = button(container, text);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static void layout(Container parent) {
        parent.doLayout();
        for (Component component : parent.getComponents()) if (component instanceof Container container) layout(container);
    }
    private static List<Component> interactiveControls(Container parent) {
        List<Component> controls = new java.util.ArrayList<>();
        collectInteractiveControls(parent, controls);
        return controls;
    }
    private static void collectInteractiveControls(Component component, List<Component> controls) {
        if (component instanceof AbstractButton || component instanceof JComboBox<?> || component instanceof JTextComponent) {
            controls.add(component);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) collectInteractiveControls(child, controls);
        }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        assertions++;
    }
}
