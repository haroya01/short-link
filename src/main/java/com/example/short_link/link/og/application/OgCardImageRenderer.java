package com.example.short_link.link.og.application;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Renders a 1200×630 PNG that crawlers fetch as the link's og:image when the destination has no
 * preview image of its own. The card shows the click count in a large headline plus the short URL —
 * the goal is that every share advertises kurl by surfacing how many times the link has already
 * been clicked.
 *
 * <p>Design tokens mirror the frontend brand surface (kurl.me): white background, accent-600
 * (#059669) mark, slate-900 headline, Pretendard Bold/Regular. Mark geometry is a 1:1 port of
 * {@code components/logo.tsx} (viewBox 28×18, three rounded bars).
 *
 * <p>Pure AWT/ImageIO so we keep the dependency tree clean. PNG bytes are returned uncached; the
 * controller is responsible for HTTP cache headers.
 */
@Component
public class OgCardImageRenderer {

  private static final int WIDTH = 1200;
  private static final int HEIGHT = 630;
  private static final int PADDING = 80;

  private static final Color SURFACE = new Color(0xFFFFFF);
  private static final Color BRAND_PRIMARY = new Color(0x059669); // accent-600
  private static final Color BRAND_SHEEN = new Color(0xECFDF5); // accent-50
  private static final Color INK_HEADLINE = new Color(0x0F172A); // slate-900
  private static final Color INK_BODY = new Color(0x475569); // slate-600

  private static final Font PRETENDARD_BOLD = loadFont("/fonts/Pretendard-Bold.otf");
  private static final Font PRETENDARD_REGULAR = loadFont("/fonts/Pretendard-Regular.otf");

  private static final Font WORDMARK_FONT = PRETENDARD_BOLD.deriveFont(48f);
  private static final Font BODY_FONT = PRETENDARD_REGULAR.deriveFont(30f);
  private static final Font FOOTER_FONT = PRETENDARD_BOLD.deriveFont(26f);

  private static Font loadFont(String resource) {
    try (InputStream in = OgCardImageRenderer.class.getResourceAsStream(resource)) {
      if (in == null) throw new IllegalStateException("missing font resource " + resource);
      return Font.createFont(Font.TRUETYPE_FONT, in);
    } catch (IOException | FontFormatException e) {
      throw new IllegalStateException("failed to load font " + resource, e);
    }
  }

  public byte[] render(String shortUrl, long clickCount) throws IOException {
    BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    try {
      enableHighQuality(g);

      g.setColor(SURFACE);
      g.fillRect(0, 0, WIDTH, HEIGHT);

      g.setColor(BRAND_SHEEN);
      g.fillOval(WIDTH - 360, -260, 700, 700);

      int markHeight = 44;
      int markTopY = PADDING;
      int markWidth = drawMark(g, PADDING, markTopY, markHeight);

      g.setColor(INK_HEADLINE);
      g.setFont(WORDMARK_FONT);
      int wordmarkBaseline = markTopY + markHeight - 2;
      g.drawString("kurl", PADDING + markWidth + 14, wordmarkBaseline);

      String headline = formatCount(clickCount) + (clickCount == 1 ? " click" : " clicks");
      Font headlineFont = pickHeadlineFont(g, headline);
      g.setFont(headlineFont);
      g.setColor(INK_HEADLINE);
      int headlineY = HEIGHT / 2 + 50;
      g.drawString(headline, PADDING, headlineY);

      g.setFont(BODY_FONT);
      g.setColor(INK_BODY);
      String displayUrl = trim(shortUrl, 60);
      g.drawString(displayUrl, PADDING, headlineY + 56);

      g.setFont(FOOTER_FONT);
      g.setColor(BRAND_PRIMARY);
      g.drawString("Shortened + tracked with kurl", PADDING, HEIGHT - PADDING);
    } finally {
      g.dispose();
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
    ImageIO.write(img, "png", out);
    return out.toByteArray();
  }

  // Mirrors components/logo.tsx Mark (viewBox 28×18). AWT roundRect arc args are diameters, so
  // the SVG rx=1.7 (radius) becomes 3.4 here. Returns the on-screen mark width for layout.
  private static int drawMark(Graphics2D g, int originX, int originY, int markHeight) {
    double s = markHeight / 18.0;
    g.setColor(BRAND_PRIMARY);
    int barH = (int) Math.round(3.4 * s);
    int arc = (int) Math.round(3.4 * s);
    g.fillRoundRect(
        originX + (int) Math.round(6 * s),
        originY + (int) Math.round(1 * s),
        (int) Math.round(20 * s),
        barH,
        arc,
        arc);
    g.fillRoundRect(
        originX, originY + (int) Math.round(7.3 * s), (int) Math.round(28 * s), barH, arc, arc);
    g.fillRoundRect(
        originX + (int) Math.round(9 * s),
        originY + (int) Math.round(13.6 * s),
        (int) Math.round(17 * s),
        barH,
        arc,
        arc);
    return (int) Math.round(28 * s);
  }

  private static void enableHighQuality(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
  }

  private static Font pickHeadlineFont(Graphics2D g, String text) {
    int max = WIDTH - PADDING * 2;
    for (int size : new int[] {200, 180, 160, 140, 120, 100, 84}) {
      Font candidate = PRETENDARD_BOLD.deriveFont((float) size);
      if (g.getFontMetrics(candidate).stringWidth(text) <= max) return candidate;
    }
    return PRETENDARD_BOLD.deriveFont(72f);
  }

  private static String formatCount(long n) {
    if (n < 0) n = 0;
    if (n < 1_000) return String.valueOf(n);
    if (n < 1_000_000) {
      return n % 1_000 == 0 ? n / 1_000 + "K" : String.format("%.1fK", n / 1_000.0);
    }
    return n % 1_000_000 == 0 ? n / 1_000_000 + "M" : String.format("%.1fM", n / 1_000_000.0);
  }

  private static String trim(String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max - 1) + "…";
  }
}
