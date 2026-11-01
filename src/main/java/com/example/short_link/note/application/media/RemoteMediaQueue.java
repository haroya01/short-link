package com.example.short_link.note.application.media;

import com.example.short_link.common.image.ImageVariantProperties;
import com.example.short_link.common.storage.ObjectStorage;
import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RemoteMediaQueue {

  private final RemoteMediaCopier copier;
  private final ThreadPoolExecutor executor;
  private final boolean copying;
  private final Map<String, Set<Long>> pending = new HashMap<>();
  private final AtomicBoolean sweeping = new AtomicBoolean();

  public RemoteMediaQueue(
      RemoteMediaCopier copier,
      RemoteMediaProperties props,
      ImageVariantProperties variants,
      ObjectStorage storage) {
    this.copier = copier;
    this.executor =
        storage.isConfigured()
            ? new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(props.queue()),
                work ->
                    Thread.ofPlatform()
                        .name("kurl-remote-media")
                        .daemon()
                        .priority(Thread.MIN_PRIORITY)
                        .unstarted(work),
                new ThreadPoolExecutor.AbortPolicy())
            : null;
    this.copying = executor != null && props.enabled() && variants.enabled();
  }

  public synchronized boolean enqueue(RemoteImage image) {
    if (!copying) {
      return false;
    }
    String key = RemoteMediaKeys.keyOf(image.url(), image.type());
    Set<Long> waiting = pending.get(key);
    if (waiting != null) {
      waiting.add(image.mediaId());
      return true;
    }
    pending.put(key, new LinkedHashSet<>(List.of(image.mediaId())));
    try {
      executor.execute(() -> copier.copy(key, image, take(key)));
      return true;
    } catch (RejectedExecutionException full) {
      pending.remove(key);
      log.warn("remote-media queue full key={}", key);
      return false;
    }
  }

  public boolean sweep() {
    if (executor == null || !sweeping.compareAndSet(false, true)) {
      return false;
    }
    try {
      executor.execute(
          () -> {
            try {
              copier.sweep();
            } finally {
              sweeping.set(false);
            }
          });
      return true;
    } catch (RejectedExecutionException full) {
      sweeping.set(false);
      return false;
    }
  }

  public boolean copying() {
    return copying;
  }

  private synchronized List<Long> take(String key) {
    return List.copyOf(pending.remove(key));
  }

  @PreDestroy
  void stop() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }
}
