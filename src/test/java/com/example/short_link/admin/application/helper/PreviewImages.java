package com.example.short_link.admin.application.helper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

public final class PreviewImages {

  private PreviewImages() {}

  public static byte[] checkerboard(int width, int height, String format) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    for (int x = 0; x < width; x += 40) {
      for (int y = 0; y < height; y += 40) {
        g.setColor(((x + y) / 40) % 2 == 0 ? Color.BLACK : Color.WHITE);
        g.fillRect(x, y, 40, 40);
      }
    }
    g.dispose();
    return encode(image, format);
  }

  public static byte[] blankPng(int width, int height) {
    return encode(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY), "png");
  }

  public static byte[] gif(int width, int height) {
    return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "gif");
  }

  private static byte[] encode(BufferedImage image, String format) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, format, out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
