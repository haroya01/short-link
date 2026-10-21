package com.example.short_link.federation.scheduler;

import com.example.short_link.federation.application.trends.LatestPosts;
import com.example.short_link.federation.application.trends.LatestProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

// One low-priority thread of its own pulls the chosen servers' newest posts every interval, whether
// or not anyone reads. The first pull waits one interval, so the day's count has been read at start
// before it. It does not run where scheduling or pulling is off.
@Slf4j
@Component
public class LatestWorker implements SmartLifecycle {

  private final LatestPosts latest;
  private final LatestProperties props;
  private final boolean scheduling;

  private volatile boolean running;
  private volatile Thread thread;

  public LatestWorker(
      LatestPosts latest,
      LatestProperties props,
      @Value("${short-link.scheduling.enabled:true}") boolean scheduling) {
    this.latest = latest;
    this.props = props;
    this.scheduling = scheduling;
  }

  @Override
  public void start() {
    running = true;
    if (props.pulling() && scheduling) {
      thread =
          Thread.ofPlatform()
              .name("kurl-latest")
              .daemon()
              .priority(Thread.MIN_PRIORITY)
              .start(this::work);
    }
  }

  @Override
  public void stop() {
    running = false;
    Thread working = thread;
    if (working != null) {
      working.interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void work() {
    while (running) {
      try {
        Thread.sleep(props.every());
      } catch (InterruptedException stopping) {
        Thread.currentThread().interrupt();
        return;
      }
      try {
        long started = System.nanoTime();
        int stored = latest.pull();
        log.info(
            "federation latest pulled stored={} tookMs={}",
            stored,
            (System.nanoTime() - started) / 1_000_000);
      } catch (RuntimeException failed) {
        log.warn("federation latest pull failed reason={}", failed.toString());
      }
    }
  }
}
