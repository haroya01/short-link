package com.example.short_link.campaign.application;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * QR 코드 PNG 인코더. ErrorCorrectionLevel.M (15% 손상 허용) — 인쇄물에 적당한 균형. 너무 높으면 데이터 밀도가 떨어지고, 너무 낮으면 인쇄
 * 흠집/오염에 약함.
 */
@Component
public class QrPngEncoder {

  private static final int DEFAULT_SIZE_PX = 512;

  public byte[] encode(String url) {
    return encode(url, DEFAULT_SIZE_PX);
  }

  public byte[] encode(String url, int sizePx) {
    try {
      Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
      hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
      hints.put(EncodeHintType.MARGIN, 1);
      hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
      BitMatrix matrix =
          new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(matrix, "PNG", out);
      return out.toByteArray();
    } catch (WriterException | IOException e) {
      throw new IllegalStateException("QR encoding failed for " + url, e);
    }
  }
}
