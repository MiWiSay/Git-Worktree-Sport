package com.miwi.gitworktree.ui;

import com.miwi.gitworktree.model.WorktreeEntry;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** 使用真正的 Swing EDT、树模型和计时器验证定位与闪烁，不操作用户仓库。 */
public final class HighlightChecks {
    private static int assertions;
    private static final Path BASE = Path.of(System.getProperty("java.io.tmpdir"), "worktree highlight tests").toAbsolutePath();
    private static final WorktreeEntry FIRST = entry("one/same");
    private static final WorktreeEntry TARGET = entry("two/same");
    private static final DefaultMutableTreeNode ROOT = new DefaultMutableTreeNode("root");
    private static JTree tree;
    private static WorktreeHighlight highlight;

    public static void main(String[] args) throws Exception {
        try {
            onEdt(() -> {
                tree = new JTree(new DefaultTreeModel(ROOT));
                tree.setRootVisible(false);
                highlight = new WorktreeHighlight(tree);
                rebuild(false);
                highlight.request(BASE.resolve("two/../two/same"));
                highlight.afterRebuild(ROOT);
                check(tree.getSelectionPath() == null, "Absent target is not replaced by same-named worktree");
                check(!highlight.isTarget(FIRST), "Pending target does not flash another worktree");
                rebuild(true);
                highlight.afterRebuild(ROOT);
                check(highlight.isTarget(TARGET), "Pending normalized path resolved after fresh data arrives");
                check(!highlight.isTarget(FIRST), "Duplicate name and HEAD do not affect exact-path matching");
                check(highlight.isLit(), "Highlight starts blue immediately");
                var selected = tree.getSelectionPath();
                check(((DefaultMutableTreeNode) selected.getLastPathComponent()).getUserObject() == TARGET, "Exact worktree selected");
                check(tree.isVisible(selected), "Nested duplicate group expanded and target visible");
            });
            AtomicBoolean sawOff = new AtomicBoolean();
            for (int i = 0; i < 20; i++) {
                Thread.sleep(50);
                onEdt(() -> { if (highlight.isTarget(TARGET) && !highlight.isLit()) sawOff.set(true); });
            }
            onEdt(() -> {
                check(sawOff.get(), "Blue alternates with normal background");
                rebuild(true);
                highlight.afterRebuild(ROOT);
                check(highlight.isTarget(TARGET), "Refresh retains the active path despite replacing nodes");
            });
            Thread.sleep(1450);
            onEdt(() -> {
                check(!highlight.isTarget(TARGET), "Highlight ends after two seconds and rebuild does not restart it");
                check(!highlight.isLit(), "Temporary color cleared after animation");
                highlight.request(Path.of(FIRST.path()));
                highlight.afterRebuild(ROOT);
                check(highlight.isTarget(FIRST), "Repeated creation can highlight another entry");
                highlight.request(Path.of(TARGET.path()));
                check(!highlight.isTarget(FIRST), "New request cancels previous target before refresh");
                highlight.afterRebuild(ROOT);
                check(highlight.isTarget(TARGET), "Latest creation wins");
                highlight.close();
                check(!highlight.isTarget(TARGET) && !highlight.isLit(), "Content disposal immediately clears animation");
            });
            Thread.sleep(300);
            onEdt(() -> check(!highlight.isLit(), "Stopped timer cannot restore highlight"));
            onEdt(() -> {
                highlight.request(Path.of(TARGET.path()));
                highlight.close();
                highlight.afterRebuild(ROOT);
                check(!highlight.isTarget(TARGET), "Disposal clears pending requests too");
                highlight.request(Path.of(TARGET.path().replace('\\', '/')));
                highlight.afterRebuild(ROOT);
                check(highlight.isTarget(TARGET), "Path separator variants resolve the same worktree");
                highlight.close();
                highlight.request(Path.of(TARGET.path()));
                highlight.afterRebuild(ROOT);
                rebuild(false);
                highlight.afterRebuild(ROOT);
                check(!highlight.isTarget(TARGET), "Removing active target cancels animation");
            });
            System.out.println("PASS: " + assertions + " Swing highlight assertions");
        } finally {
            onEdt(() -> { if (highlight != null) highlight.close(); });
        }
    }

    private static void rebuild(boolean includeTarget) {
        ROOT.removeAllChildren();
        var repository = new DefaultMutableTreeNode("repository");
        var group = new DefaultMutableTreeNode("same");
        ROOT.add(repository);
        repository.add(group);
        group.add(new DefaultMutableTreeNode(FIRST));
        if (includeTarget) group.add(new DefaultMutableTreeNode(TARGET));
        ((DefaultTreeModel) tree.getModel()).reload();
        tree.collapseRow(0);
    }

    private static WorktreeEntry entry(String relativePath) {
        return new WorktreeEntry(BASE.resolve("repository").toString(), BASE.resolve(relativePath).toString(),
                "feature/same", "0123456789abcdef", false, false, false, false, false);
    }

    private static void onEdt(Runnable runnable) throws Exception { SwingUtilities.invokeAndWait(runnable); }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
