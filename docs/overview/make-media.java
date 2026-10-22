import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Frames unchanged reference screenshots on consistent Marketplace informational slides. */
class MakeMedia {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path destination = Path.of(args[1]);
        Files.createDirectories(destination);
        String[][] slides = {
            {"banner.png", "00-banner.png", "One repository. Parallel workspaces.", "A focused worktree manager for everyday IDE work.", "Approved promotional banner; illustration only, not a literal plugin screen."},
            {"worktree-list.png", "01-worktree-manager.png", "All workspaces. One tree.", "Inspect status, full HEAD, folder creation time and path.", "Reference screenshot supplied by the author. Repository data is obscured."},
            {"branch-menu.png", "02-create-entry.png", "Start from a branch", "Choose Create Worktree in Git Branches, or right-click empty list space.", "Only the highlighted creation action is provided by this plugin."},
            {"create-worktree.png", "03-create-workspace.png", "Choose how your task begins", "Existing branch, new branch, detached HEAD, or orphan branch.", "No-checkout is optional, off by default, and unavailable for orphan mode."},
            {"settings.png", "04-language-settings.png", "Follow your IDE. Or choose a language.", "The current release includes ten editable language packs.", "Reference screenshot shows the earlier two-pack selector."},
            {"edit-language.png", "05-translation-editor.png", "Your language. Your wording.", "Read-only keys, editable values, validated import/export, and Ctrl+F search.", "Reference screenshot: older two-language tabs. Current packs are catalog-driven."},
            {"language-list.png", "06-language-catalog.png", "Manage language names independently", "Keep stable file IDs separate from display names and translations.", "Reference screenshot supplied by the author. Additional languages can be added."}
        };
        for (String[] slide : slides) {
            BufferedImage source = ImageIO.read(root.resolve(slide[0]).toFile());
            BufferedImage canvas = new BufferedImage(1600, 1000, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(25, 43, 69), 1600, 1000, new Color(16, 21, 30)));
            g.fillRect(0, 0, 1600, 1000);
            g.setColor(new Color(134, 184, 255));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            g.drawString("GIT WORKTREE SPORT  /  1.0.0", 64, 52);
            g.setColor(new Color(238, 243, 251));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
            g.drawString(slide[2], 64, 108);
            g.setColor(new Color(185, 201, 222));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
            g.drawString(slide[3], 64, 151);
            double scale = Math.min(1.5, Math.min(1472.0 / source.getWidth(), 730.0 / source.getHeight()));
            int width = (int) Math.round(source.getWidth() * scale);
            int height = (int) Math.round(source.getHeight() * scale);
            int x = (1600 - width) / 2, y = 184 + (730 - height) / 2;
            g.setColor(new Color(49, 64, 85));
            g.fillRoundRect(x - 3, y - 3, width + 6, height + 6, 16, 16);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(source, x, y, width, height, null);
            g.setColor(new Color(175, 192, 215));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            g.drawString(slide[4], 64, 959);
            g.dispose();
            ImageIO.write(canvas, "png", destination.resolve(slide[1]).toFile());
        }
        System.out.println("PASS: Seven 1600x1000 informational slides; source screenshots unchanged and aspect ratios preserved");
    }
}
