package com.example.short_link.common.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import javax.imageio.ImageIO;

final class ImageFixtures {

  private ImageFixtures() {}

  static byte[] photo(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    Random random = new Random(width * 31L + height);
    for (int y = 0; y < height; y += 40) {
      for (int x = 0; x < width; x += 40) {
        g.setColor(new Color(random.nextInt(0xFFFFFF)));
        g.fillRect(x, y, 40, 40);
      }
    }
    g.dispose();
    return encode(image, "jpg");
  }

  static byte[] encode(BufferedImage image, String format) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, format, out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static BufferedImage decode(byte[] bytes) {
    try {
      return ImageIO.read(new ByteArrayInputStream(bytes));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static byte[] resource(String name) {
    try (InputStream in = ImageFixtures.class.getResourceAsStream("/image-variants/" + name)) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static byte[] withOrientation(byte[] jpeg, int orientation, ByteOrder order) {
    ByteBuffer tiff = ByteBuffer.allocate(26).order(order);
    tiff.put(order == ByteOrder.BIG_ENDIAN ? new byte[] {'M', 'M'} : new byte[] {'I', 'I'});
    tiff.putShort((short) 42).putInt(8).putShort((short) 1);
    tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1);
    tiff.putShort((short) orientation).putShort((short) 0).putInt(0);
    byte[] exif = new byte[6 + tiff.capacity()];
    System.arraycopy(new byte[] {'E', 'x', 'i', 'f', 0, 0}, 0, exif, 0, 6);
    System.arraycopy(tiff.array(), 0, exif, 6, tiff.capacity());
    int length = exif.length + 2;
    byte[] out = new byte[jpeg.length + 2 + length];
    out[0] = (byte) 0xFF;
    out[1] = (byte) 0xD8;
    out[2] = (byte) 0xFF;
    out[3] = (byte) 0xE1;
    out[4] = (byte) (length >> 8);
    out[5] = (byte) length;
    System.arraycopy(exif, 0, out, 6, exif.length);
    System.arraycopy(jpeg, 2, out, 6 + exif.length, jpeg.length - 2);
    return out;
  }
}
