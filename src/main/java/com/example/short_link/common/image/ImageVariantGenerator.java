package com.example.short_link.common.image;

import com.example.short_link.common.image.ImageVariantLedger.Outcome;
import com.example.short_link.common.storage.ObjectStorage;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageVariantGenerator {

  private final ObjectStorage storage;
  private final ImageVariantRenderer renderer;
  private final ImageVariantLedger ledger;

  public synchronized void generate(String key) {
    long started = System.nanoTime();
    try {
      if (ledger.recorded(key)) {
        log(key, "skipped", started, 0, 0, null);
        return;
      }
      Optional<byte[]> source = storage.getObject(key);
      if (source.isEmpty()) {
        ledger.record(key, Outcome.FAILED);
        log(key, "failed", started, 0, 0, "missing");
        return;
      }
      byte[] original = source.get();
      ImageVariantRenderer.Rendered rendered;
      try {
        rendered = renderer.render(original, ImageVariantKeys.png(key));
      } catch (IOException unreadable) {
        ledger.record(key, Outcome.FAILED);
        log(key, "failed", started, original.length, 0, "undecodable");
        return;
      }
      putVariants(key, rendered);
      ledger.record(key, Outcome.MADE);
      log(key, "made", started, original.length, size(rendered), null);
    } catch (RuntimeException transientFailure) {
      log(key, "failed", started, 0, 0, transientFailure.getClass().getSimpleName());
    }
  }

  public synchronized boolean storeWithVariants(String key, byte[] original, long maxSourcePixels) {
    long started = System.nanoTime();
    ImageVariantRenderer.Rendered rendered;
    try {
      rendered = renderer.render(original, ImageVariantKeys.png(key), maxSourcePixels);
    } catch (IOException unreadable) {
      log(key, "failed", started, original.length, 0, unreadable.getMessage());
      return false;
    }
    storage.putObject(key, ImageVariantKeys.contentType(key), original);
    putVariants(key, rendered);
    ledger.record(key, Outcome.MADE);
    log(key, "made", started, original.length, size(rendered), null);
    return true;
  }

  private void putVariants(String key, ImageVariantRenderer.Rendered rendered) {
    String contentType = ImageVariantKeys.contentType(key);
    storage.putObject(
        ImageVariantKeys.variantKey(key, ImageVariantKeys.LARGE), contentType, rendered.large());
    storage.putObject(
        ImageVariantKeys.variantKey(key, ImageVariantKeys.SMALL), contentType, rendered.small());
  }

  private static long size(ImageVariantRenderer.Rendered rendered) {
    return (long) rendered.large().length + rendered.small().length;
  }

  private static void log(
      String key, String outcome, long started, long bytesIn, long bytesOut, String reason) {
    long ms = (System.nanoTime() - started) / 1_000_000;
    if (reason == null) {
      log.info(
          "image-variant key={} outcome={} ms={} bytes_in={} bytes_out={}",
          key,
          outcome,
          ms,
          bytesIn,
          bytesOut);
    } else {
      log.warn(
          "image-variant key={} outcome={} ms={} bytes_in={} bytes_out={} reason={}",
          key,
          outcome,
          ms,
          bytesIn,
          bytesOut,
          reason);
    }
  }
}
