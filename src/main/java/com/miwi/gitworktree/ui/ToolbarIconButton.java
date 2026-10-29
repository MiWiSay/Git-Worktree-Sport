package com.miwi.gitworktree.ui;

import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.ui.InplaceButton;
import com.intellij.util.ui.JBUI;

import javax.swing.Icon;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;

/** IDE-style icon button with a rounded theme-aware hover/pressed background and swappable progress icon. */
public final class ToolbarIconButton extends InplaceButton {
    private final Icon normalIcon;
    private final Icon hoverIcon;
    private final Icon inactiveIcon;

    public ToolbarIconButton(String tooltip, Icon icon, ActionListener action) {
        super(new IconButton(tooltip, icon), action);
        normalIcon = icon;
        hoverIcon = icon;
        inactiveIcon = icon;
        setFillBg(false);
        setPainting(true);
        setToolTipText(tooltip);
        setPreferredSize(JBUI.size(28, 28));
        setMinimumSize(JBUI.size(28, 28));
        setMaximumSize(JBUI.size(28, 28));
    }

    public void setBusy(boolean busy, Icon progressIcon) {
        setIcons(new IconButton(getToolTipText(), busy ? progressIcon : normalIcon,
                busy ? progressIcon : hoverIcon, busy ? progressIcon : inactiveIcon));
        setActive(!busy);
    }

    @Override protected void paintHover(Graphics graphics, boolean pressed) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(pressed ? JBUI.CurrentTheme.ActionButton.pressedBackground()
                : JBUI.CurrentTheme.ActionButton.hoverBackground());
        int arc = JBUI.scale(8);
        g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, arc, arc);
        g.setColor(JBUI.CurrentTheme.ActionButton.hoverBorder());
        g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
        g.dispose();
    }
}
