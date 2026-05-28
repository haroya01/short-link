package com.example.short_link.campaign.application.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class QrPngEncoderTest {

  private final QrPngEncoder encoder = new QrPngEncoder();

  @Test
  void defaultEncodeProducesSquarePng512() throws Exception {
    byte[] png = encoder.encode("https://example.com/x");
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

    assertThat(png).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
    assertThat(image.getWidth()).isEqualTo(512);
    assertThat(image.getHeight()).isEqualTo(512);
  }

  @Test
  void honorsCustomSizeAndLevelWithoutLabel() throws Exception {
    byte[] png = encoder.encode("https://example.com/y", 256, QrPngEncoder.Ec.H, null);
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

    assertThat(image.getWidth()).isEqualTo(256);
    assertThat(image.getHeight()).isEqualTo(256);
  }

  @Test
  void blankLabelIsTreatedAsNoLabel() throws Exception {
    byte[] png = encoder.encode("https://example.com/z", 320, QrPngEncoder.Ec.L, "   ");
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

    assertThat(image.getHeight()).isEqualTo(320);
  }

  @Test
  void labelExpandsCanvasHeight() throws Exception {
    byte[] png =
        encoder.encode("https://example.com/labelled", 384, QrPngEncoder.Ec.Q, "Batch east-12");
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

    assertThat(image.getWidth()).isEqualTo(384);
    assertThat(image.getHeight()).isGreaterThan(384);
  }

  @Test
  void longLabelIsTruncatedAndStillEncodes() throws Exception {
    String wide = "L".repeat(400);
    byte[] png = encoder.encode("https://example.com/long", 256, QrPngEncoder.Ec.M, wide);

    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    assertThat(image.getHeight()).isGreaterThan(256);
  }

  @Test
  void verySmallSizeStillKeepsMinimumLabelHeight() throws Exception {
    byte[] png = encoder.encode("https://example.com/small", 64, QrPngEncoder.Ec.M, "tag");

    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    assertThat(image.getWidth()).isEqualTo(64);
    assertThat(image.getHeight() - 64).isGreaterThanOrEqualTo(20);
  }

  @Test
  void allErrorCorrectionLevelsEncodeCleanly() throws Exception {
    for (QrPngEncoder.Ec level : QrPngEncoder.Ec.values()) {
      byte[] png = encoder.encode("https://example.com/ec", 160, level, null);
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
      assertThat(image.getWidth()).isEqualTo(160);
    }
  }
}
