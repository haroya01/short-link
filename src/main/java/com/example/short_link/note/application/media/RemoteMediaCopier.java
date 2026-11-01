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
    Outcome outcome;
    try {
      String kept = keptKey(image.url());
      outcome =
          kept != null
              ? new Outcome(kept, "linked rows=" + show(kept, image, mediaIds))
              : fetch(key, image, mediaIds);
    } catch (RuntimeException failed) {
      outcome = new Outcome(key, "failed " + failed.getClass().getSimpleName());
    }
    log.info(
        "remote-media key={} host={} outcome={} ms={}",
        outcome.key(),
        host(image.url()),
        outcome.text(),
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

  private Outcome fetch(String key, RemoteImage image, List<Long> mediaIds) {
    return switch (fetcher.fetch(image.url(), props.maxBytes())) {
      case Refused refused -> new Outcome(key, "refused " + refused.reason());
      case Image fetched -> {
        String stored = RemoteMediaKeys.keyOf(image.url(), fetched.type());
        yield new Outcome(stored, store(stored, image, mediaIds, fetched.bytes()));
      }
    };
  }

  private String keptKey(String url) {
    for (String key : RemoteMediaKeys.keysOf(url)) {
      if (copies.kept(key)) {
        return key;
      }
    }
    return null;
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

  private record Outcome(String key, String text) {}

  private static String host(String url) {
    try {
      return URI.create(url).getHost();
    } catch (IllegalArgumentException malformed) {
      return "?";
    }
  }
}
