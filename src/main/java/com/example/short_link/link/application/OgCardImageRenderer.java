package com.example.short_link.link.application;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Renders a 1200×630 PNG that crawlers fetch as the link's og:image when the destination has no
 * preview image of its own. The card shows the click count in a large headline plus the short URL —
 * the goal is that every share advertises kurl by surfacing how many times the link has already
 * been clicked.
 *
 * <p>Pure AWT/ImageIO so we keep the dependency tree clean. PNG bytes are returned uncached; the
 * controller is responsible for HTTP cache headers.
 */
@Component
public class OgCardImageRenderer {

  private static final int WIDTH = 1200;
  private static final int HEIGHT = 630;
  private static final int PADDING = 80;

  private static final Color BG = new Color(0x0F172A); // slate-900
  private static final Color FG = new Color(0xF8FAFC); // slate-50
  private static final Color MUTED = new Color(0xCBD5E1); // slate-300
  private static final Color ACCENT = new Color(0x22D3EE); // cyan-400 ish

  public byte[] render(String shortUrl, long clickCount) throws IOException {
    BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(
          RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

      // Background
      g.setColor(BG);
      g.fillRect(0, 0, WIDTH, HEIGHT);

      // Subtle accent corner glow
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f));
      g.setColor(ACCENT);
      g.fillOval(WIDTH - 320, -200, 600, 600);
      g.setComposite(AlphaComposite.SrcOver);

      // Brand wordmark (top-left)
      g.setColor(MUTED);
      g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 28));
      g.drawString("kurl.me", PADDING, PADDING + 8);

      // Click count headline
      g.setColor(FG);
      String headline = formatCount(clickCount) + (clickCount == 1 ? " click" : " clicks");
      Font headlineFont = pickHeadlineFont(g, headline);
      g.setFont(headlineFont);
      int headlineY = HEIGHT / 2 + 40;
      g.drawString(headline, PADDING, headlineY);

      // Short URL beneath
      g.setColor(MUTED);
      g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 30));
      String displayUrl = trim(shortUrl, 60);
      g.drawString(displayUrl, PADDING, headlineY + 60);

      // Footer hint
      g.setColor(ACCENT);
      g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
      g.drawString("shortened + tracked with kurl", PADDING, HEIGHT - PADDING);
    } finally {
      g.dispose();
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
    ImageIO.write(img, "png", out);
    return out.toByteArray();
  }

  private static Font pickHeadlineFont(Graphics2D g, String text) {
    int max = WIDTH - PADDING * 2;
    for (int size : new int[] {180, 160, 140, 120, 100, 84}) {
      Font candidate = new Font(Font.SANS_SERIF, Font.BOLD, size);
      if (g.getFontMetrics(candidate).stringWidth(text) <= max) return candidate;
    }
    return new Font(Font.SANS_SERIF, Font.BOLD, 72);
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
