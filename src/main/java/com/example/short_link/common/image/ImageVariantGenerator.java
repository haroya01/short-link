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

  public void generate(String key) {
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
      boolean png = ImageVariantKeys.png(key);
      ImageVariantRenderer.Rendered rendered;
      try {
        rendered = renderer.render(original, png);
      } catch (IOException unreadable) {
        ledger.record(key, Outcome.FAILED);
        log(key, "failed", started, original.length, 0, "undecodable");
        return;
      }
      String contentType = ImageVariantKeys.contentType(key);
      storage.putObject(
          ImageVariantKeys.variantKey(key, ImageVariantKeys.LARGE), contentType, rendered.large());
      storage.putObject(
          ImageVariantKeys.variantKey(key, ImageVariantKeys.SMALL), contentType, rendered.small());
      ledger.record(key, Outcome.MADE);
      log(
          key,
          "made",
          started,
          original.length,
          rendered.large().length + rendered.small().length,
          null);
    } catch (RuntimeException transientFailure) {
      log(key, "failed", started, 0, 0, transientFailure.getClass().getSimpleName());
    }
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
