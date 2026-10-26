import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Vẽ ảnh nền giao diện "TiviKit" cho Home sạch (26/10/2026, thay theme/vgs.jpg có logo VGS, không thuộc MIT): nền chàm,
 * hình tivi + ngôi sao của logo TiviKit (res/drawable/logo_tivikit.xml, khung 108) cỡ lớn, mờ, nghiêng nhẹ ở nửa phải;
 * nửa trái và phía trên để trống cho đồng hồ. Nhiễu dùng hạt giống cố định: chạy lại với cùng JDK ra cùng ảnh.
 * Dùng (JDK 17): java -Djava.awt.headless=true scripts/AnhNenTiviKit.java app/src/main/assets/theme/tivikit.jpg
 */
public class AnhNenTiviKit {
    static final int W = 1920, H = 1080;

    /** Khung tivi (viền + chân đế) theo logo. */
    static Area tivi() {
        Area a = new Area(new RoundRectangle2D.Double(29, 33, 50, 36, 14, 14));
        a.subtract(new Area(new RoundRectangle2D.Double(34.5, 38.5, 39, 25, 7, 7)));
        a.add(new Area(new Rectangle2D.Double(51, 68, 6, 6)));
        a.add(new Area(new RoundRectangle2D.Double(43, 73, 22, 4.5, 4.5, 4.5)));
        return a;
    }

    /** Ngôi sao 4 cánh của logo, tâm (cx, cy), bán kính r (tỉ lệ đường cong như path gốc). */
    static Path2D sao(double cx, double cy, double r) {
        double a = 0.94 / 8.5 * r, b = 2.47 / 8.5 * r;
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - r);
        p.curveTo(cx + a, cy - b, cx + b, cy - a, cx + r, cy);
        p.curveTo(cx + b, cy + a, cx + a, cy + b, cx, cy + r);
        p.curveTo(cx - a, cy + b, cx - b, cy + a, cx - r, cy);
        p.curveTo(cx - b, cy - a, cx - a, cy - b, cx, cy - r);
        p.closePath();
        return p;
    }

    static AffineTransform dat(double x, double y, double tiLe, double doNghieng) {
        AffineTransform t = new AffineTransform();
        t.translate(x, y);
        t.rotate(Math.toRadians(doNghieng));
        t.scale(tiLe, tiLe);
        t.translate(-54, -55);
        return t;
    }

    static Color mau(int rgb, int alpha) { return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha); }

    public static void main(String[] args) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Nền: chàm đậm ở góc trái trên (chữ đồng hồ trắng đọc rõ) sang tím chàm ở góc phải dưới.
        g.setPaint(new LinearGradientPaint(0, 0, W, H, new float[] {0f, 0.55f, 1f},
            new Color[] {mau(0x080B2A, 255), mau(0x16156A, 255), mau(0x3024B0, 255)}));
        g.fillRect(0, 0, W, H);
        // Quầng sáng chàm (màu #5E5CE6 của logo) sau hình tivi, quầng xanh (#0A84FF) phía trên phải.
        g.setPaint(new RadialGradientPaint(new Point2D.Double(1470, 640), 980, new float[] {0f, 1f},
            new Color[] {mau(0x5E5CE6, 120), mau(0x5E5CE6, 0)}));
        g.fillRect(0, 0, W, H);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(1780, 170), 700, new float[] {0f, 1f},
            new Color[] {mau(0x0A84FF, 60), mau(0x0A84FF, 0)}));
        g.fillRect(0, 0, W, H);

        double cx = 1500, cy = 650, tiLe = 17.5, nghieng = -9;
        Area tv = tivi();

        // Lớp vọng: viền tivi lớn hơn, lệch lên trái, rất mờ — tạo chiều sâu.
        Shape vong = dat(cx - 70, cy - 45, tiLe * 1.2, nghieng).createTransformedShape(tv);
        g.setStroke(new BasicStroke(3f));
        g.setColor(mau(0xB8B6FF, 38));
        g.draw(vong);

        // Bóng đổ của hình chính.
        Shape bong = dat(cx + 24, cy + 28, tiLe, nghieng).createTransformedShape(tv);
        g.setColor(mau(0x07052A, 110));
        g.fill(bong);

        // Hình chính: tô chuyển lavender sang xanh của logo, trong suốt.
        Shape chinh = dat(cx, cy, tiLe, nghieng).createTransformedShape(tv);
        Rectangle2D k = chinh.getBounds2D();
        g.setPaint(new GradientPaint((float) k.getMinX(), (float) k.getMinY(), mau(0xC9C8FF, 88),
            (float) k.getMaxX(), (float) k.getMaxY(), mau(0x6FB6FF, 52)));
        g.fill(chinh);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(mau(0xE4E3FF, 70));
        g.draw(chinh);

        // Ngôi sao lớn trong màn hình tivi + quầng sáng; ngôi sao nhỏ góc trên phải màn hình (như logo).
        AffineTransform t = dat(cx, cy, tiLe, nghieng);
        Point2D tamSao = t.transform(new Point2D.Double(54, 51), null);
        g.setPaint(new RadialGradientPaint(tamSao, 260, new float[] {0f, 1f},
            new Color[] {mau(0xFFFFFF, 55), mau(0xFFFFFF, 0)}));
        g.fill(new Ellipse2D.Double(tamSao.getX() - 260, tamSao.getY() - 260, 520, 520));
        g.setColor(mau(0xFFFFFF, 125));
        g.fill(t.createTransformedShape(sao(54, 51, 8.5)));
        g.setColor(mau(0xFFFFFF, 105));
        g.fill(t.createTransformedShape(sao(66.5, 43, 2.6)));

        // Vài ngôi sao nhỏ rải quanh, mờ.
        double[][] rai = {{1060, 210, 14, 70}, {1250, 120, 9, 55}, {1830, 470, 11, 60}, {980, 930, 8, 40}, {1700, 1010, 10, 45}};
        for (double[] s : rai) {
            g.setColor(mau(0xFFFFFF, (int) s[3]));
            g.fill(sao(s[0], s[1], s[2]));
        }
        g.dispose();

        // Rắc nhiễu nhẹ để nền chuyển màu không bị sọc khi nén JPEG / hiện trên tivi 8 bit.
        Random r = new Random(108);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int p = img.getRGB(x, y), n = (int) Math.round(r.nextGaussian() * 1.2);
            int rr = clamp(((p >> 16) & 255) + n), gg = clamp(((p >> 8) & 255) + n), bb = clamp((p & 255) + n);
            img.setRGB(x, y, (rr << 16) | (gg << 8) | bb);
        }

        ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam pr = w.getDefaultWriteParam();
        pr.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        pr.setCompressionQuality(0.86f);
        File ra = new File(args[0]);
        ra.delete();
        try (ImageOutputStream o = ImageIO.createImageOutputStream(ra)) {
            w.setOutput(o);
            w.write(null, new IIOImage(img, null, null), pr);
        }
        w.dispose();
        System.out.println(ra + " " + ra.length() + " bytes");
    }

    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
