import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

public final class IconGenerator {
    public static void main(String[] args) throws Exception {
        int size = 512;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(17, 21, 18));
        g.fillRoundRect(0, 0, size, size, 116, 116);
        g.setColor(new Color(190, 255, 46));
        g.fillRoundRect(44, 44, 424, 424, 88, 88);
        g.setColor(new Color(16, 20, 17));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 260));
        g.drawString("A", 155, 368);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 100));
        g.drawString("·", 360, 250);
        g.dispose();
        File target = new File(args.length == 0 ? "assets/icon.png" : args[0]);
        target.getParentFile().mkdirs();
        ImageIO.write(image, "png", target);
    }
}
