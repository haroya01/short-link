package com.example.short_link.federation.scheduler;

import com.example.short_link.federation.application.directory.DirectoryProperties;
import com.example.short_link.federation.application.directory.DirectorySync;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// A run takes minutes of paced requests, so it goes to one low-priority thread of its own rather
// than the shared scheduler thread; a run asked for while one is going is not started. No thread
// exists where directories or scheduling are off.
@Slf4j
@Component
public class DirectoryWorker {

  private final DirectorySync sync;
  private final ExecutorService executor;
  private final AtomicBoolean running = new AtomicBoolean();

  public DirectoryWorker(
      DirectorySync sync,
      DirectoryProperties props,
      @Value("${short-link.scheduling.enabled:true}") boolean scheduling) {
    this.sync = sync;
    this.executor =
        props.enabled() && scheduling
            ? Executors.newSingleThreadExecutor(
                work ->
                    Thread.ofPlatform()
                        .name("kurl-directory")
                        .daemon()
                        .priority(Thread.MIN_PRIORITY)
                        .unstarted(work))
            : null;
  }

  @Scheduled(cron = "${short-link.federation.directory.cron:0 40 4,16 * * *}", zone = "Asia/Seoul")
  public void scheduled() {
    trigger();
  }

  public boolean trigger() {
    if (executor == null || !running.compareAndSet(false, true)) {
      return false;
    }
    executor.execute(
        () -> {
          try {
            sync.run();
          } catch (RuntimeException failed) {
            log.warn("federation directory run failed reason={}", failed.toString());
          } finally {
            running.set(false);
          }
        });
    return true;
  }

  public boolean running() {
    return running.get();
  }

  @PreDestroy
  void stop() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }
}
