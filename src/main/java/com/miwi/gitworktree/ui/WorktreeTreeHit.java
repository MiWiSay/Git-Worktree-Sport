package com.miwi.gitworktree.ui;

import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.awt.Point;
import java.awt.Rectangle;

/** Resolves a context-menu target by the visible row, including whitespace after its label. */
final class WorktreeTreeHit {
    private WorktreeTreeHit() {}

    static TreePath pathAt(JTree tree, Point point) {
        if (point.y < 0 || point.y >= tree.getHeight()) return null;
        int row = tree.getClosestRowForLocation(0, point.y);
        if (row < 0) return null;
        Rectangle bounds = tree.getRowBounds(row);
        if (bounds == null || point.y < bounds.y || point.y >= bounds.y + bounds.height) return null;
        return tree.getPathForRow(row);
    }
}
