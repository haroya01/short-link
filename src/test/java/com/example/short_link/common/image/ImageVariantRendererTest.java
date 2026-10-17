package com.example.short_link.common.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class ImageVariantRendererTest {

  private final ImageVariantRenderer renderer = new ImageVariantRenderer();

  @Test
  void aPhotoTurnedByExifComesOutUprightWithoutTheTag() throws IOException {
    ImageVariantRenderer.Rendered rendered =
        renderer.render(ImageFixtures.resource("rotated-6.jpg"), false);

    BufferedImage large = ImageFixtures.decode(rendered.large());
    assertThat(large.getWidth()).isEqualTo(32);
    assertThat(large.getHeight()).isEqualTo(64);
    assertThat(red(large.getRGB(28, 4))).isTrue();
    assertThat(red(large.getRGB(4, 4))).isFalse();
    assertThat(red(large.getRGB(28, 60))).isFalse();
    assertThat(ExifOrientation.of(rendered.large())).isEqualTo(1);
    assertThat(ExifOrientation.of(rendered.small())).isEqualTo(1);
    assertThat(containsExif(rendered.large())).isFalse();
  }

  @Test
  void everyOrientationPutsTheTopLeftCornerWhereTheViewerSeesIt() throws IOException {
    byte[] marked = ImageFixtures.encode(marked(80, 40), "jpg");
    String[] corner = {"", "TL", "TR", "BR", "BL", "TL", "TR", "BR", "BL"};
    for (int orientation = 1; orientation <= 8; orientation++) {
      byte[] source = ImageFixtures.withOrientation(marked, orientation, ByteOrder.LITTLE_ENDIAN);
      BufferedImage large = ImageFixtures.decode(renderer.render(source, false).large());

      boolean swapped = orientation >= 5;
      assertThat(large.getWidth()).as("orientation %d", orientation).isEqualTo(swapped ? 40 : 80);
      assertThat(large.getHeight()).as("orientation %d", orientation).isEqualTo(swapped ? 80 : 40);
      assertThat(redCorner(large)).as("orientation %d", orientation).isEqualTo(corner[orientation]);
    }
  }

  @Test
  void aFeedPhotoBecomes1080And480WideJpegs() throws IOException {
    ImageVariantRenderer.Rendered rendered =
        renderer.render(ImageFixtures.photo(1800, 2400), false);

    BufferedImage large = ImageFixtures.decode(rendered.large());
    BufferedImage small = ImageFixtures.decode(rendered.small());
    assertThat(large.getWidth()).isEqualTo(1080);
    assertThat(large.getHeight()).isEqualTo(1440);
    assertThat(small.getWidth()).isEqualTo(480);
    assertThat(small.getHeight()).isEqualTo(640);
    assertThat(jpeg(rendered.large())).isTrue();
    assertThat(jpeg(rendered.small())).isTrue();
  }

  @Test
  void neverUpscalesButStillWritesBoth() throws IOException {
    ImageVariantRenderer.Rendered tiny = renderer.render(ImageFixtures.photo(300, 200), false);
    assertThat(ImageFixtures.decode(tiny.large()).getWidth()).isEqualTo(300);
    assertThat(ImageFixtures.decode(tiny.small()).getWidth()).isEqualTo(300);
    assertThat(ImageFixtures.decode(tiny.small()).getHeight()).isEqualTo(200);

    ImageVariantRenderer.Rendered between = renderer.render(ImageFixtures.photo(700, 350), false);
    assertThat(ImageFixtures.decode(between.large()).getWidth()).isEqualTo(700);
    assertThat(ImageFixtures.decode(between.small()).getWidth()).isEqualTo(480);
    assertThat(ImageFixtures.decode(between.small()).getHeight()).isEqualTo(240);
  }

  @Test
  void aPngStaysAPngAndKeepsItsTransparency() throws IOException {
    BufferedImage source = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = source.createGraphics();
    g.setColor(Color.GREEN);
    g.fillRect(600, 0, 600, 800);
    g.dispose();

    ImageVariantRenderer.Rendered rendered =
        renderer.render(ImageFixtures.encode(source, "png"), true);

    assertThat(png(rendered.large())).isTrue();
    assertThat(png(rendered.small())).isTrue();
    BufferedImage large = ImageFixtures.decode(rendered.large());
    BufferedImage small = ImageFixtures.decode(rendered.small());
    assertThat(large.getWidth()).isEqualTo(1080);
    assertThat(small.getWidth()).isEqualTo(480);
    assertThat(large.getColorModel().hasAlpha()).isTrue();
    assertThat(large.getRGB(10, 10) >>> 24).isZero();
    assertThat(small.getRGB(10, 10) >>> 24).isZero();
    assertThat(small.getRGB(470, 10) >>> 24).isEqualTo(0xFF);
  }

  @Test
  void bytesThatAreNotAnImageAreRefused() {
    assertThatThrownBy(() -> renderer.render(new byte[] {1, 2, 3, 4}, false))
        .isInstanceOf(IOException.class);
    byte[] truncated = java.util.Arrays.copyOf(ImageFixtures.photo(400, 300), 200);
    assertThatThrownBy(() -> renderer.render(truncated, false)).isInstanceOf(IOException.class);
  }

  @Test
  void decodingSkipsPixelsSoAFullResolutionRasterIsNeverHeld() throws IOException {
    assertThat(ImageVariantRenderer.subsampling(1800, 2400, 1)).isEqualTo(1);
    assertThat(ImageVariantRenderer.subsampling(4032, 3024, 1)).isEqualTo(3);
    assertThat(ImageVariantRenderer.subsampling(3024, 4032, 1)).isEqualTo(2);
    assertThat(ImageVariantRenderer.subsampling(4032, 3024, 6)).isEqualTo(2);
    int tall = ImageVariantRenderer.subsampling(2000, 20000, 1);
    assertThat((long) (2000 / tall + 1) * (20000 / tall + 1))
        .isLessThanOrEqualTo(ImageVariantRenderer.MAX_DECODED_PIXELS);

    byte[] phone = ImageFixtures.photo(4032, 3024);
    BufferedImage decoded = ImageVariantRenderer.decode(phone, 1);
    assertThat(decoded.getWidth()).isEqualTo(1344);
    assertThat(decoded.getHeight()).isEqualTo(1008);

    com.sun.management.ThreadMXBean threads =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long before = threads.getCurrentThreadAllocatedBytes();
    renderer.render(phone, false);
    long allocated = threads.getCurrentThreadAllocatedBytes() - before;
    long fullRaster = 4032L * 3024 * 3;
    assertThat(allocated).isLessThan(fullRaster);
  }

  private static BufferedImage marked(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.BLUE);
    g.fillRect(0, 0, width, height);
    g.setColor(Color.RED);
    g.fillRect(0, 0, 16, 16);
    g.dispose();
    return image;
  }

  private static String redCorner(BufferedImage image) {
    int right = image.getWidth() - 5;
    int bottom = image.getHeight() - 5;
    StringBuilder found = new StringBuilder();
    if (red(image.getRGB(4, 4))) found.append("TL");
    if (red(image.getRGB(right, 4))) found.append("TR");
    if (red(image.getRGB(right, bottom))) found.append("BR");
    if (red(image.getRGB(4, bottom))) found.append("BL");
    return found.toString();
  }

  private static boolean red(int rgb) {
    int r = (rgb >> 16) & 0xFF;
    int b = rgb & 0xFF;
    return r > 200 && b < 60;
  }

  private static boolean jpeg(byte[] bytes) {
    return (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
  }

  private static boolean png(byte[] bytes) {
    return (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
  }

  private static boolean containsExif(byte[] bytes) {
    for (int i = 0; i + 4 < bytes.length; i++) {
      if (bytes[i] == 'E' && bytes[i + 1] == 'x' && bytes[i + 2] == 'i' && bytes[i + 3] == 'f') {
        return true;
      }
    }
    return false;
  }
}
