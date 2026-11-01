package com.example.short_link.note.application.media;

import com.example.short_link.common.image.ImageVariantGenerator;
import com.example.short_link.common.image.ImageVariantLedger;
import com.example.short_link.common.net.PublicImageFetcher;
import com.example.short_link.common.net.PublicImageFetcher.Image;
import com.example.short_link.common.net.PublicImageFetcher.Refused;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.ObjectStorageException;
import com.example.short_link.common.storage.ObjectStoragePublicUrls;
import com.example.short_link.note.domain.repository.RemoteMediaRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemoteMediaCopier {

  static final long MAX_SOURCE_PIXELS = 50_000_000L;

  private final PublicImageFetcher fetcher;
  private final ImageVariantGenerator variants;
  private final ImageVariantLedger ledger;
  private final ObjectStorage storage;
  private final ObjectStoragePublicUrls publicUrls;
  private final RemoteMediaRepository copies;
  private final RemoteMediaProperties props;
  private final Clock clock;

  public void copy(String key, RemoteImage image, List<Long> mediaIds) {
    long started = System.nanoTime();
    String outcome;
    try {
      outcome =
          copies.kept(key)
              ? "linked rows=" + show(key, image, mediaIds)
              : fetch(key, image, mediaIds);
    } catch (RuntimeException failed) {
      outcome = "failed " + failed.getClass().getSimpleName();
    }
    log.info(
        "remote-media key={} host={} outcome={} ms={}",
        key,
        host(image.url()),
        outcome,
        (System.nanoTime() - started) / 1_000_000);
  }

  public void sweep() {
    try {
      int expired = drop(copies.expired(now().minus(props.keepFor()), props.sweepBatch()));
      int orphaned = drop(copies.orphaned(props.sweepBatch()));
      if (expired + orphaned > 0) {
        log.info("remote-media sweep expired={} orphaned={}", expired, orphaned);
      }
    } catch (RuntimeException failed) {
      log.warn("remote-media sweep failed", failed);
    }
  }

  private String fetch(String key, RemoteImage image, List<Long> mediaIds) {
    return switch (fetcher.fetch(image.url(), image.type(), props.maxBytes())) {
      case Refused refused -> "refused " + refused.reason();
      case Image fetched -> store(key, image, mediaIds, fetched.bytes());
    };
  }

  private String store(String key, RemoteImage image, List<Long> mediaIds, byte[] bytes) {
    try {
      if (!variants.storeWithVariants(key, bytes, MAX_SOURCE_PIXELS)) {
        return "undecodable";
      }
      int shown = show(key, image, mediaIds);
      if (shown == 0) {
        discard(key);
        return "gone";
      }
      return "made rows=" + shown;
    } catch (RuntimeException failed) {
      discard(key);
      throw failed;
    }
  }

  private int show(String key, RemoteImage image, List<Long> mediaIds) {
    return copies.attach(key, image.url(), publicUrls.forKey(key), mediaIds, now());
  }

  private int drop(List<String> keys) {
    int dropped = 0;
    for (String key : keys) {
      copies.detach(key);
      if (discard(key)) {
        copies.forget(key);
        dropped++;
      }
    }
    return dropped;
  }

  private boolean discard(String key) {
    try {
      storage.deleteWithVariants(key);
    } catch (ObjectStorageException failed) {
      log.warn("remote-media key={} delete failed", key, failed);
      return false;
    }
    ledger.forget(key);
    return true;
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  private static String host(String url) {
    try {
      return URI.create(url).getHost();
    } catch (IllegalArgumentException malformed) {
      return "?";
    }
  }
}
