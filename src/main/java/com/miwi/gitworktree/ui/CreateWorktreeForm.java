package com.miwi.gitworktree.ui;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.miwi.gitworktree.service.WorktreeService;
import com.miwi.gitworktree.service.WorktreeService.BranchReference;
import com.miwi.gitworktree.service.WorktreeService.CreateField;
import com.miwi.gitworktree.service.WorktreeService.CreateIssue;
import com.miwi.gitworktree.service.WorktreeService.CreateMode;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Creation UI kept separate from DialogWrapper so field, layout and validation flows are testable. */
public final class CreateWorktreeForm extends JPanel {
    private final JTextField pathField = new JTextField(42);
    private final JTextField branchNameField;
    private final JLabel parallelBranchPrefix = new JLabel();
    private final JCheckBox parallelMode = new JCheckBox(I18n.text("create.parallelMode"));
    private final JLabel parallelModeHint = new JLabel();
    private final JComboBox<BranchReference> startBranch;
    private final JTextField startReference = new JTextField(30);
    private final JCheckBox customReference = new JCheckBox(I18n.text("create.otherReference"));
    private final JCheckBox noCheckout = new JCheckBox(I18n.text("create.noCheckout"));
    private final List<JRadioButton> modeButtons = new ArrayList<>();
    private final EnumMap<CreateField, JComponent> inputs = new EnumMap<>(CreateField.class);
    private final EnumMap<CreateField, Border> originalBorders = new EnumMap<>(CreateField.class);
    private final Map<Component, Boolean> enabledBeforeBusy = new IdentityHashMap<>();
    private final List<Component> busyComponents = new ArrayList<>();
    private final JPanel referencePanel = new JPanel(new BorderLayout(6, 4));
    private final JPanel referenceOptions = new JPanel(new BorderLayout(6, 4));
    private final JLabel branchLabel = new JLabel(I18n.text("create.branchName"));
    private final JLabel referenceLabel = new JLabel(I18n.text("create.startBranch"));
    private final Path repositoryRoot;
    private final String selectedBranchName;
    private Runnable layoutChangeListener = () -> { };
    private boolean autoPath = true;
    private boolean updatingPath;

    public CreateWorktreeForm(Path repositoryRoot, WorktreeService.BranchSpec selectedBranch,
                              List<BranchReference> availableBranches, boolean derived,
                              Consumer<Consumer<String>> browseFolder) {
        super(new BorderLayout(8, JBUI.scale(4)));
        this.repositoryRoot = repositoryRoot.toAbsolutePath().normalize();
        this.selectedBranchName = selectedBranch.localName();
        branchNameField = new JTextField("new-" + selectedBranchName, 30);
        String selectedRef = selectedBranch.startPoint() == null
                ? "refs/heads/" + selectedBranchName : selectedBranch.startPoint();
        // The invoking branch remains first, with full ref values separate from translated display text.
        LinkedHashMap<String, BranchReference> choices = new LinkedHashMap<>();
        choices.put(selectedRef, new BranchReference(selectedRef, selectedBranch.remote()
                ? selectedRef.replaceFirst("^refs/remotes/", "") : selectedBranchName, selectedBranch.remote()));
        availableBranches.forEach(branch -> choices.putIfAbsent(branch.reference(), branch));
        startBranch = new JComboBox<>(choices.values().toArray(BranchReference[]::new));
        startBranch.setEditable(false);
        startBranch.setMaximumRowCount(18);
        startBranch.setAlignmentX(Component.LEFT_ALIGNMENT);
        customReference.setAlignmentX(Component.LEFT_ALIGNMENT);
        startReference.setAlignmentX(Component.LEFT_ALIGNMENT);
        startBranch.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof BranchReference branch) {
                    setText(I18n.format(branch.remote() ? "create.remoteBranch" : "create.localBranch", branch.name()));
                }
                return this;
            }
        });
        startReference.setText(selectedRef);
        referenceOptions.setLayout(new BoxLayout(referenceOptions, BoxLayout.Y_AXIS));
        referenceOptions.add(startBranch);
        referenceOptions.add(customReference);
        referenceOptions.add(startReference);
        referencePanel.add(referenceOptions, BorderLayout.CENTER);
        inputs.put(CreateField.PATH, pathField);
        inputs.put(CreateField.BRANCH, branchNameField);
        // Reference selection errors use the combo or the advanced editor, never a hidden input.
        originalBorders.put(CreateField.PATH, pathField.getBorder());
        originalBorders.put(CreateField.BRANCH, branchNameField.getBorder());
        startBranch.putClientProperty("normalBorder", startBranch.getBorder());
        startReference.putClientProperty("normalBorder", startReference.getBorder());

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = JBUI.insets(4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2; c.weightx = 1;
        // New key avoids the old saved destination-folder hint overriding this redesigned heading.
        fields.add(new JLabel(I18n.text("create.chooseMethod")), c);
        JPanel modes = new JPanel();
        // A single horizontal row; preferred width includes every option, including long translations.
        modes.setLayout(new BoxLayout(modes, BoxLayout.X_AXIS));
        ButtonGroup group = new ButtonGroup();
        if (!derived) addMode(modes, group, CreateMode.EXISTING_BRANCH, "create.mode.existing", true);
        addMode(modes, group, CreateMode.NEW_BRANCH, "create.mode.new", derived);
        addMode(modes, group, CreateMode.DETACHED, "create.mode.detached", false);
        // An orphan has no ancestry, so it does not belong to the "from this branch" shortcut.
        if (!derived) addMode(modes, group, CreateMode.ORPHAN, "create.mode.orphan", false);
        c.gridy++;
        fields.add(modes, c);
        c.gridy++;
        parallelMode.setToolTipText(I18n.text("create.parallelModeHint"));
        parallelMode.addActionListener(event -> {
            updateParallelMode();
            updateSuggestedPath();
            revalidate();
            repaint();
            layoutChangeListener.run();
        });
        parallelBranchPrefix.setText(parallelPrefixLabel());
        parallelModeHint.setText("<html><div style='width:" + JBUI.scale(520) + "px'>"
                + I18n.text("create.parallelModeHint") + "</div></html>");
        parallelModeHint.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        JPanel parallelOptions = new JPanel(new BorderLayout(0, 2));
        parallelOptions.add(parallelMode, BorderLayout.NORTH);
        parallelOptions.add(parallelModeHint, BorderLayout.CENTER);
        fields.add(parallelOptions, c);
        c.gridwidth = 1; c.weightx = 0; c.gridy++;
        fields.add(branchLabel, c);
        c.gridx = 1; c.weightx = 1;
        JPanel branchNameInput = new JPanel(new BorderLayout(4, 0));
        branchNameInput.add(parallelBranchPrefix, BorderLayout.WEST);
        branchNameInput.add(branchNameField, BorderLayout.CENTER);
        fields.add(branchNameInput, c);
        c.gridx = 0; c.weightx = 0; c.gridy++;
        fields.add(referenceLabel, c);
        c.gridx = 1; c.weightx = 1;
        fields.add(referencePanel, c);
        c.gridx = 0; c.gridy++; c.gridwidth = 2;
        fields.add(noCheckout, c);
        JPanel pathRow = new JPanel(new BorderLayout(6, 0));
        pathRow.add(pathField, BorderLayout.CENTER);
        JPanel pathButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton browse = new JButton(I18n.text("action.browse"));
        browse.addActionListener(event -> browseFolder.accept(path -> {
            autoPath = false;
            pathField.setText(path);
            clearError(CreateField.PATH);
        }));
        JButton reset = new JButton(I18n.text("create.resetPath"));
        reset.addActionListener(event -> { autoPath = true; updateSuggestedPath(); });
        pathButtons.add(browse);
        pathButtons.add(reset);
        pathRow.add(pathButtons, BorderLayout.EAST);
        // Keep the destination row in the same grid as the options. If it is a
        // separate BorderLayout.SOUTH component, DialogWrapper can stretch the
        // center area and create a large blank region between the advanced option
        // and the path editor.
        c.gridx = 0; c.gridy++; c.gridwidth = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        fields.add(pathRow, c);
        // Keep any extra height below the complete form, not between the advanced
        // options and the destination row. This also keeps the mode row anchored
        // when a mode hides some of its controls.
        c.gridy++; c.weighty = 1; c.fill = GridBagConstraints.VERTICAL;
        fields.add(new JPanel(), c);
        add(fields, BorderLayout.CENTER);

        onEdit(branchNameField, () -> { clearError(CreateField.BRANCH); updateSuggestedPath(); });
        onEdit(pathField, () -> {
            clearError(CreateField.PATH);
            if (!updatingPath) autoPath = false;
        });
        onEdit(startReference, () -> clearError(CreateField.START_POINT));
        startBranch.addActionListener(event -> clearError(CreateField.START_POINT));
        customReference.addActionListener(event -> {
            clearError(CreateField.START_POINT);
            if (customReference.isSelected() && startBranch.getSelectedItem() instanceof BranchReference branch) {
                startReference.setText(branch.reference());
            }
            updateReferenceMode();
        });
        updateMode();
    }

    /** Lets the owning dialog repack after a mode changes the visible rows. */
    public void setLayoutChangeListener(Runnable listener) {
        layoutChangeListener = listener == null ? () -> { } : listener;
    }

    /** Prevent edits and secondary actions while a submission is waiting on Git. */
    public void setBusy(boolean busy) {
        if (busy) {
            if (!enabledBeforeBusy.isEmpty()) return;
            disableInteractiveControls(this);
            return;
        }
        // Restore child controls before their parent. Swing combo boxes can
        // reset their renderer button when the parent is enabled again.
        for (int index = busyComponents.size() - 1; index >= 0; index--) {
            Component component = busyComponents.get(index);
            Boolean enabled = enabledBeforeBusy.get(component);
            if (enabled != null) component.setEnabled(enabled);
        }
        enabledBeforeBusy.clear();
        busyComponents.clear();
    }

    private void disableInteractiveControls(Component component) {
        if (component instanceof AbstractButton || component instanceof JComboBox<?> || component instanceof JTextComponent) {
            enabledBeforeBusy.put(component, component.isEnabled());
            busyComponents.add(component);
            component.setEnabled(false);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) disableInteractiveControls(child);
        }
    }

    private void addMode(JPanel row, ButtonGroup group, CreateMode mode, String key, boolean selected) {
        if (row.getComponentCount() > 0) row.add(Box.createHorizontalStrut(JBUI.scale(8)));
        JRadioButton button = new JRadioButton(I18n.text(key), selected);
        button.putClientProperty("mode", mode);
        button.addActionListener(event -> updateMode());
        modeButtons.add(button);
        group.add(button);
        row.add(button);
    }

    public CreateMode mode() {
        return modeButtons.stream().filter(JRadioButton::isSelected)
                .map(button -> (CreateMode) button.getClientProperty("mode")).findFirst().orElseThrow();
    }

    private void updateMode() {
        boolean named = mode() == CreateMode.NEW_BRANCH || mode() == CreateMode.ORPHAN;
        branchNameField.setVisible(named);
        branchLabel.setVisible(named);
        boolean supportsParallelMode = mode() == CreateMode.NEW_BRANCH;
        if (!supportsParallelMode) parallelMode.setSelected(false);
        parallelMode.setVisible(supportsParallelMode);
        parallelModeHint.setVisible(supportsParallelMode);
        parallelBranchPrefix.setVisible(supportsParallelMode && parallelMode.isSelected());
        boolean needsStart = mode() == CreateMode.NEW_BRANCH || mode() == CreateMode.DETACHED;
        referenceLabel.setVisible(needsStart);
        referencePanel.setVisible(needsStart);
        boolean orphan = mode() == CreateMode.ORPHAN;
        if (orphan) noCheckout.setSelected(false);
        noCheckout.setEnabled(!orphan);
        noCheckout.setToolTipText(I18n.text(orphan ? "create.noCheckoutOrphanHint" : "create.noCheckoutHint"));
        updateReferenceMode();
        updateSuggestedPath();
        revalidate();
        repaint();
        layoutChangeListener.run();
    }

    private void updateReferenceMode() {
        boolean advanced = customReference.isSelected();
        startBranch.setVisible(!advanced);
        startReference.setVisible(advanced);
        referenceLabel.setText(I18n.text(advanced ? "create.startPoint" : "create.startBranch"));
        revalidate();
        repaint();
    }

    private void updateParallelMode() {
        parallelBranchPrefix.setText(parallelPrefixLabel());
        parallelBranchPrefix.setVisible(parallelMode.isSelected() && mode() == CreateMode.NEW_BRANCH);
    }

    private static String parallelPrefixLabel() {
        String prefix = WorktreePreferences.parallelBranchPrefix();
        return prefix.isEmpty() ? "" : prefix + "/";
    }

    private String branchName() {
        String name = branchNameField.getText().trim();
        return parallelMode.isSelected() && mode() == CreateMode.NEW_BRANCH
                ? WorktreePreferences.parallelBranchName(name) : name;
    }

    private void updateSuggestedPath() {
        if (!autoPath || updatingPath) return;
        String name = mode() == CreateMode.NEW_BRANCH || mode() == CreateMode.ORPHAN
                ? branchName() : selectedBranchName;
        if (mode() == CreateMode.DETACHED) name += "-detached";
        String safe = name.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if (safe.isBlank()) safe = "branch";
        String suggested = repositoryRoot.getParent().resolve(repositoryRoot.getFileName() + "-" + safe).toString();
        if (pathField.getText().equals(suggested)) return;
        updatingPath = true;
        try { pathField.setText(suggested); }
        finally { updatingPath = false; }
    }

    public String path() { return pathField.getText().trim(); }

    private String reference() {
        if (customReference.isSelected()) return startReference.getText().trim();
        return startBranch.getSelectedItem() instanceof BranchReference branch ? branch.reference() : "";
    }

    public List<CreateIssue> requiredIssues() {
        List<CreateIssue> issues = new ArrayList<>();
        if (path().isBlank()) issues.add(new CreateIssue(CreateField.PATH, I18n.text("error.pathRequired")));
        if ((mode() == CreateMode.NEW_BRANCH || mode() == CreateMode.ORPHAN) && branchNameField.getText().isBlank()) {
            issues.add(new CreateIssue(CreateField.BRANCH, I18n.format("error.branchInvalid", "")));
        }
        if ((mode() == CreateMode.NEW_BRANCH || mode() == CreateMode.DETACHED) && reference().isBlank()) {
            issues.add(new CreateIssue(CreateField.START_POINT, I18n.format("error.startPointInvalid", "")));
        }
        return List.copyOf(issues);
    }

    public WorktreeService.CreateSpec spec() {
        return new WorktreeService.CreateSpec(mode(), branchName(), reference(), noCheckout.isSelected());
    }

    public void markIssues(List<CreateIssue> issues) {
        for (CreateIssue issue : issues) {
            JComponent field = input(issue.field());
            Border original = originalBorder(issue.field());
            field.setBorder(new ErrorBorder(original));
        }
        if (!issues.isEmpty()) input(issues.getFirst().field()).requestFocusInWindow();
    }

    private JComponent input(CreateField field) {
        return field == CreateField.START_POINT ? (customReference.isSelected() ? startReference : startBranch) : inputs.get(field);
    }

    private Border originalBorder(CreateField field) {
        return field == CreateField.START_POINT ? (Border) input(field).getClientProperty("normalBorder") : originalBorders.get(field);
    }

    private void clearError(CreateField field) {
        if (field == CreateField.START_POINT) {
            startBranch.setBorder((Border) startBranch.getClientProperty("normalBorder"));
            startReference.setBorder((Border) startReference.getClientProperty("normalBorder"));
            return;
        }
        input(field).setBorder(originalBorder(field));
    }

    private static void onEdit(JTextField field, Runnable callback) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { callback.run(); }
            @Override public void removeUpdate(DocumentEvent event) { callback.run(); }
            @Override public void changedUpdate(DocumentEvent event) { callback.run(); }
        });
    }

    /** Keep the IDE border insets so error feedback never changes field height or text padding. */
    private static final class ErrorBorder extends AbstractBorder {
        private final Border original;
        private ErrorBorder(Border original) { this.original = original; }
        @Override public Insets getBorderInsets(Component component, Insets insets) {
            Insets source = original == null ? JBUI.insets(2) : original.getBorderInsets(component);
            insets.set(source.top, source.left, source.bottom, source.right);
            return insets;
        }
        @Override public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new JBColor(new Color(224, 128, 128), new Color(224, 145, 145)));
                g.setStroke(new BasicStroke(JBUI.scale(1)));
                g.drawRoundRect(x + 1, y + 1, width - 3, height - 3, JBUI.scale(6), JBUI.scale(6));
            } finally { g.dispose(); }
        }
    }
}
