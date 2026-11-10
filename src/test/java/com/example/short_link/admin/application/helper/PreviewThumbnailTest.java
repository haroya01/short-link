package com.example.short_link.admin.application.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class PreviewThumbnailTest {

  @Test
  void theClearPreviewIsASmallJpegThatKeepsTheDetail() throws IOException {
    BufferedImage shown =
        read(PreviewThumbnail.render(PreviewImages.checkerboard(1200, 630, "png"), true));

    assertThat(shown.getWidth()).isEqualTo(PreviewThumbnail.EDGE);
    assertThat(shown.getHeight()).isEqualTo(168);
    assertThat(largestStep(shown)).isGreaterThan(150);
  }

  @Test
  void theDefaultPreviewIsBlurredOnTheServerBeforeItLeaves() throws IOException {
    BufferedImage shown =
        read(PreviewThumbnail.render(PreviewImages.checkerboard(1200, 630, "jpg"), false));

    assertThat(shown.getWidth()).isEqualTo(PreviewThumbnail.EDGE);
    assertThat(shown.getHeight()).isEqualTo(168);
    assertThat(largestStep(shown)).isLessThan(24);
  }

  @Test
  void aTallImageFitsTheSameSquare() throws IOException {
    BufferedImage shown =
        read(PreviewThumbnail.render(PreviewImages.checkerboard(300, 1500, "png"), false));

    assertThat(shown.getHeight()).isEqualTo(PreviewThumbnail.EDGE);
    assertThat(shown.getWidth()).isEqualTo(64);
  }

  @Test
  void anImageOverThePixelLimitIsRefusedBeforeItIsDecoded() {
    byte[] huge = PreviewImages.blankPng(6000, 5000);

    assertThatThrownBy(() -> PreviewThumbnail.render(huge, false))
        .isInstanceOf(PreviewThumbnail.TooManyPixels.class)
        .hasMessage("too many pixels: 6000x5000");
  }

  @Test
  void onlyJpegAndPngAreDecoded() {
    byte[] gif = PreviewImages.gif(40, 20);

    assertThatThrownBy(() -> PreviewThumbnail.render(gif, true))
        .isInstanceOf(IIOException.class)
        .hasMessageContaining("not a JPEG or PNG");
  }

  @Test
  void bytesThatAreNoImageAreRefused() {
    assertThatThrownBy(() -> PreviewThumbnail.render("<html></html>".getBytes(), true))
        .isInstanceOf(IIOException.class);
  }

  private static BufferedImage read(byte[] jpeg) throws IOException {
    assertThat(jpeg).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    return ImageIO.read(new ByteArrayInputStream(jpeg));
  }

  private static int largestStep(BufferedImage image) {
    int largest = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 1; x < image.getWidth(); x++) {
        largest = Math.max(largest, Math.abs(luma(image, x, y) - luma(image, x - 1, y)));
      }
    }
    return largest;
  }

  private static int luma(BufferedImage image, int x, int y) {
    int rgb = image.getRGB(x, y);
    return (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
  }
}
