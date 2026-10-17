package com.example.short_link.common.image;

import com.example.short_link.common.storage.ObjectStorage;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// One image at a time on one low-priority thread: a decode holds tens of megabytes and the box has
// little to spare. A full queue drops the job; the sweep finds the original again.
@Slf4j
@Component
public class ImageVariantQueue {

  private final ImageVariantGenerator generator;
  private final ThreadPoolExecutor executor;
  private final Set<String> pending = ConcurrentHashMap.newKeySet();

  public ImageVariantQueue(
      ImageVariantGenerator generator, ImageVariantProperties props, ObjectStorage storage) {
    this.generator = generator;
    this.executor =
        props.enabled() && storage.isConfigured()
            ? new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(props.queue()),
                work ->
                    Thread.ofPlatform()
                        .name("kurl-image-variants")
                        .daemon()
                        .priority(Thread.MIN_PRIORITY)
                        .unstarted(work),
                new ThreadPoolExecutor.AbortPolicy())
            : null;
  }

  public boolean enqueue(String key) {
    if (executor == null || !ImageVariantKeys.eligible(key) || !pending.add(key)) {
      return false;
    }
    try {
      executor.execute(
          () -> {
            try {
              generator.generate(key);
            } finally {
              pending.remove(key);
            }
          });
      return true;
    } catch (RejectedExecutionException full) {
      pending.remove(key);
      log.warn("image-variant queue full key={}", key);
      return false;
    }
  }

  public boolean enabled() {
    return executor != null;
  }

  public boolean idle() {
    return pending.isEmpty();
  }

  @PreDestroy
  void stop() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }
}
