package com.example.short_link.common.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

@Component
public class ImageVariantRenderer {

  static final float JPEG_QUALITY = 0.82f;
  static final long MAX_DECODED_PIXELS = 2L * ImageVariantKeys.LARGE * 2880;

  public Rendered render(byte[] source, boolean png) throws IOException {
    int orientation = ExifOrientation.of(source);
    BufferedImage large = upright(decode(source, orientation), orientation, png);
    BufferedImage small = shrink(large, Math.min(ImageVariantKeys.SMALL, large.getWidth()), png);
    return new Rendered(encode(large, png), encode(small, png));
  }

  static BufferedImage decode(byte[] source, int orientation) throws IOException {
    try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) {
        throw new IIOException("no decoder for this image");
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        int step = subsampling(reader.getWidth(0), reader.getHeight(0), orientation);
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(step, step, 0, 0);
        return reader.read(0, param);
      } catch (RuntimeException unreadable) {
        throw new IIOException("undecodable image", unreadable);
      } finally {
        reader.dispose();
      }
    }
  }

  static int subsampling(int width, int height, int orientation) {
    int uprightWidth = ExifOrientation.swapsAxes(orientation) ? height : width;
    int step = Math.max(1, uprightWidth / ImageVariantKeys.LARGE);
    while ((long) ceilDiv(width, step) * ceilDiv(height, step) > MAX_DECODED_PIXELS) {
      step++;
    }
    return step;
  }

  private static int ceilDiv(int value, int divisor) {
    return (value + divisor - 1) / divisor;
  }

  private static BufferedImage upright(BufferedImage source, int orientation, boolean png) {
    int w = source.getWidth();
    int h = source.getHeight();
    boolean swap = ExifOrientation.swapsAxes(orientation);
    int uprightWidth = swap ? h : w;
    int uprightHeight = swap ? w : h;
    int targetWidth = Math.min(ImageVariantKeys.LARGE, uprightWidth);
    double scale = (double) targetWidth / uprightWidth;
    int targetHeight = Math.max(1, (int) Math.round(uprightHeight * scale));
    AffineTransform transform = AffineTransform.getScaleInstance(scale, scale);
    transform.concatenate(orient(orientation, w, h));
    return paint(source, transform, targetWidth, targetHeight, png);
  }

  private static BufferedImage shrink(BufferedImage source, int targetWidth, boolean png) {
    BufferedImage current = source;
    while (current.getWidth() / 2 >= targetWidth) {
      current = scaled(current, current.getWidth() / 2, png);
    }
    return current.getWidth() == targetWidth ? current : scaled(current, targetWidth, png);
  }

  private static BufferedImage scaled(BufferedImage source, int targetWidth, boolean png) {
    double scale = (double) targetWidth / source.getWidth();
    int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
    return paint(
        source, AffineTransform.getScaleInstance(scale, scale), targetWidth, targetHeight, png);
  }

  private static BufferedImage paint(
      BufferedImage source, AffineTransform transform, int width, int height, boolean png) {
    BufferedImage target =
        new BufferedImage(
            width, height, png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    Graphics2D g = target.createGraphics();
    try {
      if (!png) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
      }
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(source, transform, null);
    } finally {
      g.dispose();
    }
    return target;
  }

  private static AffineTransform orient(int orientation, int w, int h) {
    return switch (orientation) {
      case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);
      case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);
      case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);
      case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
      case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);
      case 7 -> new AffineTransform(0, -1, -1, 0, h, w);
      case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);
      default -> new AffineTransform();
    };
  }

  private static byte[] encode(BufferedImage image, boolean png) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageWriter writer = ImageIO.getImageWritersByFormatName(png ? "png" : "jpeg").next();
    try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
      ImageWriteParam param = writer.getDefaultWriteParam();
      if (!png) {
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
      }
      writer.setOutput(stream);
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  public record Rendered(byte[] large, byte[] small) {}
}
