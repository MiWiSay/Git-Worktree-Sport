package com.miwi.gitworktree.ui;

import com.miwi.gitworktree.model.WorktreeEntry;
import com.miwi.gitworktree.service.WorktreePaths;

import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** EDT 上按完整路径定位工作树；闪烁不依赖名称、分组或刷新前的节点实例。 */
final class WorktreeHighlight implements AutoCloseable {
    private static final long DURATION = TimeUnit.SECONDS.toNanos(2);
    private static final long PULSE_INTERVAL = TimeUnit.MILLISECONDS.toNanos(250);
    private final JTree tree;
    private final Timer timer;
    private Path pendingPath;
    private Path activePath;
    private long startedAt;
    private boolean lit;

    WorktreeHighlight(JTree tree) {
        this.tree = tree;
        timer = new Timer(50, event -> updatePulse());
    }

    void request(Path path) {
        close();
        pendingPath = WorktreePaths.identity(path);
    }

    void afterRebuild(DefaultMutableTreeNode root) {
        Path target = pendingPath != null ? pendingPath : activePath;
        if (target == null) return;
        TreePath found = find(root, target);
        if (found == null) {
            // 列表可能尚未包含新建项；保留待定位路径，等待下次成功刷新。
            if (pendingPath == null) close();
            return;
        }
        tree.expandPath(found.getParentPath());
        tree.setSelectionPath(found);
        tree.scrollPathToVisible(found);
        if (pendingPath != null) {
            activePath = pendingPath;
            pendingPath = null;
            startedAt = System.nanoTime();
            lit = true;
            timer.start();
        }
        tree.repaint();
    }

    boolean isTarget(WorktreeEntry entry) {
        return activePath != null && WorktreePaths.same(activePath, Path.of(entry.path()));
    }

    boolean isLit() { return lit; }

    private void updatePulse() {
        long elapsed = System.nanoTime() - startedAt;
        if (elapsed >= DURATION) {
            close();
            return;
        }
        // 使用实际经过时间，避免 EDT 暂时繁忙导致闪烁拖延超过两秒。
        lit = elapsed / PULSE_INTERVAL % 2 == 0;
        tree.repaint();
    }

    @Override public void close() {
        timer.stop();
        pendingPath = null;
        activePath = null;
        lit = false;
        tree.repaint();
    }

    private static TreePath find(DefaultMutableTreeNode root, Path target) {
        var nodes = root.depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (node.getUserObject() instanceof WorktreeEntry entry
                    && WorktreePaths.same(target, Path.of(entry.path()))) {
                return new TreePath(node.getPath());
            }
        }
        return null;
    }
}
