package com.miwi.gitworktree.ui;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Scales an icon's visible artwork while keeping its toolbar footprint predictable. */
public final class ScaledIcon implements Icon {
    private final Icon delegate;
    private final int size;

    public ScaledIcon(Icon delegate, int size) {
        if (delegate == null) throw new IllegalArgumentException("delegate icon is required");
        if (size < 1) throw new IllegalArgumentException("size must be positive");
        this.delegate = delegate;
        this.size = size;
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create(x, y, size, size);
        g.translate((size - delegate.getIconWidth()) / 2.0, (size - delegate.getIconHeight()) / 2.0);
        g.scale((double) size / delegate.getIconWidth(), (double) size / delegate.getIconHeight());
        delegate.paintIcon(component, g, 0, 0);
        g.dispose();
    }
}
