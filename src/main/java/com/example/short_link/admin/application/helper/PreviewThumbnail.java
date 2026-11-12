package com.example.short_link.admin.application.helper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
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

public final class PreviewThumbnail {

  public static final int EDGE = 320;
  public static final int BLURRED_EDGE = 16;
  public static final long MAX_SOURCE_PIXELS = 25_000_000L;

  private static final float JPEG_QUALITY = 0.8f;
  private static final Set<String> FORMATS = Set.of("jpeg", "png");
  private static final ReentrantLock DECODING = new ReentrantLock();

  private PreviewThumbnail() {}

  public static byte[] render(byte[] source, boolean clear) throws IOException {
    DECODING.lock();
    try {
      BufferedImage thumbnail = fit(decode(source), EDGE);
      return jpeg(clear ? thumbnail : blur(thumbnail));
    } finally {
      DECODING.unlock();
    }
  }

  private static BufferedImage decode(byte[] source) throws IOException {
    try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) {
        throw new IIOException("no decoder for this image");
      }
      ImageReader reader = readers.next();
      try {
        if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
          throw new IIOException("not a JPEG or PNG: " + reader.getFormatName());
        }
        reader.setInput(in, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if ((long) width * height > MAX_SOURCE_PIXELS) {
          throw new TooManyPixels(width, height);
        }
        int step = Math.max(1, Math.max(width, height) / (EDGE * 2));
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

  private static BufferedImage fit(BufferedImage source, int edge) {
    double scale = Math.min(1.0, (double) edge / Math.max(source.getWidth(), source.getHeight()));
    int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
    BufferedImage current = source;
    while (current.getWidth() / 2 >= width && current.getHeight() / 2 >= height) {
      current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
    }
    return draw(current, width, height);
  }

  private static BufferedImage blur(BufferedImage thumbnail) {
    return draw(fit(thumbnail, BLURRED_EDGE), thumbnail.getWidth(), thumbnail.getHeight());
  }

  private static BufferedImage draw(BufferedImage source, int width, int height) {
    BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = target.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, width, height);
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(source, 0, 0, width, height, null);
    } finally {
      g.dispose();
    }
    return target;
  }

  private static byte[] jpeg(BufferedImage image) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(JPEG_QUALITY);
      writer.setOutput(stream);
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  public static final class TooManyPixels extends IIOException {

    private static final long serialVersionUID = 1L;

    TooManyPixels(int width, int height) {
      super("too many pixels: " + width + "x" + height);
    }
  }
}
