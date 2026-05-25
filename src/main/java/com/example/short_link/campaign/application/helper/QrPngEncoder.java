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

/**
 * QR 코드 PNG 인코더. 다운로드 직전 사용자가 옵션을 줄 수 있어 (EC level / 픽셀 크기 / 라벨 텍스트) — 인쇄 발주 시 해상도와 손상 허용치를 자기 인쇄물
 * 조건에 맞춤. 라벨은 QR 바로 아래 small caption 으로 합성 (인쇄소가 어느 묶음인지 식별).
 */
@Component
public class QrPngEncoder {

  public enum Ec {
    /** L = 7% 손상 허용. 도시 인쇄물 (오염 적음) + 데이터 밀도 우선. */
    L(ErrorCorrectionLevel.L),
    /** M = 15%. 표준 균형. */
    M(ErrorCorrectionLevel.M),
    /** Q = 25%. 외부 노출, 비/오염 가능성 있는 환경. */
    Q(ErrorCorrectionLevel.Q),
    /** H = 30%. 로고 embed 가 들어올 수 있는 안전 한계 (v2 영역). */
    H(ErrorCorrectionLevel.H);

    final ErrorCorrectionLevel level;

    Ec(ErrorCorrectionLevel level) {
      this.level = level;
    }
  }

  public byte[] encode(String url) {
    return encode(url, 512, Ec.M, null);
  }

  /**
   * @param sizePx QR 폭 (px). 인쇄 mm 환산: 200dpi 가정 시 512px ≈ 65mm.
   * @param ec error correction level
   * @param labelText QR 아래 표시할 텍스트. null/blank 이면 라벨 안 박음.
   */
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

      // 라벨 박힌 캔버스 = QR + 폭 동일, 높이 = QR + labelHeight. 라벨은 검정 텍스트 흰 배경, QR 외부 mm 처럼 보이게.
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
