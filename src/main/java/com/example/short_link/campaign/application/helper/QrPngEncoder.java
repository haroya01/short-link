package com.example.short_link.campaign.application.helper;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class QrPngEncoder {

  public enum Ec {
    /** 오류 정정 수준 L: 약 7%. */
    L(ErrorCorrectionLevel.L),
    /** 오류 정정 수준 M: 약 15%. */
    M(ErrorCorrectionLevel.M),
    /** 오류 정정 수준 Q: 약 25%. */
    Q(ErrorCorrectionLevel.Q),
    /** 오류 정정 수준 H: 약 30%. */
    H(ErrorCorrectionLevel.H);

    final ErrorCorrectionLevel level;

    Ec(ErrorCorrectionLevel level) {
      this.level = level;
    }
  }

  public byte[] encode(String url) {
    return encode(url, 512, Ec.M, null);
  }

  /** 라벨은 QR 아래에 붙인다. {@code labelText}가 null/blank이면 QR만 반환한다. */
  public byte[] encode(String url, int sizePx, Ec ec, String labelText) {
    try {
      Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
      hints.put(EncodeHintType.ERROR_CORRECTION, ec.level);
      hints.put(EncodeHintType.MARGIN, 1);
      hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
      BitMatrix matrix =
          new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
      BufferedImage qr = MatrixToImageWriter.toBufferedImage(matrix);

      if (labelText == null || labelText.isBlank()) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(qr, "PNG", out);
        return out.toByteArray();
      }

      int labelHeight = Math.max(20, sizePx / 12);
      BufferedImage canvas =
          new BufferedImage(sizePx, sizePx + labelHeight, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = canvas.createGraphics();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, sizePx, sizePx + labelHeight);
        g.drawImage(qr, 0, 0, null);
        g.setColor(new Color(0x0F172A)); // slate-900
        int fontSize = Math.max(12, labelHeight - 8);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize));
        String text = truncateToFit(g, labelText, sizePx - 16);
        int textWidth = g.getFontMetrics().stringWidth(text);
        int x = (sizePx - textWidth) / 2;
        int y = sizePx + labelHeight - (labelHeight - fontSize) / 2 - 4;
        g.drawString(text, x, y);
      } finally {
        g.dispose();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(canvas, "PNG", out);
      return out.toByteArray();
    } catch (WriterException | IOException e) {
      throw new IllegalStateException("QR encoding failed for " + url, e);
    }
  }

  private static String truncateToFit(Graphics2D g, String text, int maxWidth) {
    if (g.getFontMetrics().stringWidth(text) <= maxWidth) return text;
    String ell = "…";
    int ellWidth = g.getFontMetrics().stringWidth(ell);
    int lo = 0;
    int hi = text.length();
    while (lo < hi) {
      int mid = (lo + hi + 1) / 2;
      if (g.getFontMetrics().stringWidth(text.substring(0, mid)) + ellWidth <= maxWidth) {
        lo = mid;
      } else {
        hi = mid - 1;
      }
    }
    return text.substring(0, lo) + ell;
  }
}
