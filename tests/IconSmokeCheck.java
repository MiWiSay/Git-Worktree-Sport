import com.intellij.util.SVGLoader;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 使用目标 IDE 自带 SVG 加载器验证资源尺寸和选中态配色。 */
public final class IconSmokeCheck {
    public static void main(String[] args) throws Exception {
        Path resources = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Files.createDirectories(output);
        BufferedImage preview = new BufferedImage(600, 220, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = preview.createGraphics();
        g.setColor(new Color(30, 31, 34));
        g.fillRect(0, 0, preview.getWidth(), preview.getHeight());
        String[] files = {"icons/worktreeSport.svg", "icons/worktreeSport_dark.svg",
                "icons/worktreeSport@20x20.svg", "icons/worktreeSport@20x20_dark.svg",
                "META-INF/pluginIcon.svg", "META-INF/pluginIcon_dark.svg",
                "icons/refresh.svg", "icons/refresh_dark.svg"};
        int[] sizes = {16, 16, 20, 20, 40, 40, 16, 16};
        for (int i = 0; i < files.length; i++) {
            Image icon = SVGLoader.load(resources.resolve(files[i]).toUri().toURL(), 1f);
            if (icon.getWidth(null) != sizes[i] || icon.getHeight(null) != sizes[i]) throw new AssertionError(files[i]);
            BufferedImage rendered = new BufferedImage(sizes[i], sizes[i], BufferedImage.TYPE_INT_ARGB);
            Graphics2D r = rendered.createGraphics();
            r.drawImage(icon, 0, 0, null);
            r.dispose();
            int visible = 0;
            for (int y = 0; y < sizes[i]; y++) for (int x = 0; x < sizes[i]; x++) if ((rendered.getRGB(x, y) >>> 24) > 30) visible++;
            if (visible < 30) throw new AssertionError("Empty SVG: " + files[i]);
            ImageIO.write(rendered, "png", output.resolve(Path.of(files[i]).getFileName() + ".png").toFile());
            int x = 35 + i * 95;
            g.setColor(i % 2 == 0 ? Color.WHITE : new Color(30, 31, 34));
            g.fillRoundRect(x - 12, 20, 70, 70, 12, 12);
            g.drawImage(icon, x + 10, 40, null);
        }
        String selectedSvg = Files.readString(resources.resolve(files[3])).replace("#CED0D6", "#FFFFFF");
        Image selected = SVGLoader.load(new ByteArrayInputStream(selectedSvg.getBytes(StandardCharsets.UTF_8)), 1f);
        g.setColor(new Color(53, 116, 240));
        g.fillRoundRect(35, 130, 44, 44, 12, 12);
        g.drawImage(selected, 47, 142, null);
        g.setColor(Color.WHITE);
        g.drawString("Selected palette preview (20px)", 100, 158);
        g.dispose();
        ImageIO.write(preview, "png", output.resolve("icon-preview.png").toFile());
        System.out.println("PASS: 8 SVG assets rendered by target IDE loader; 16/20/40px dimensions verified");
    }
}
