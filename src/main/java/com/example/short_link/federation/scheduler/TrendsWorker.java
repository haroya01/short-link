package com.example.short_link.federation.scheduler;

import com.example.short_link.federation.application.trends.TrendingPosts;
import com.example.short_link.federation.application.trends.TrendsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

// One low-priority thread of its own refreshes the trending posts at start and then every
// interval, so a slow server never holds up the shared scheduler. It does not run where scheduling
// or trends are off.
@Slf4j
@Component
public class TrendsWorker implements SmartLifecycle {

  private final TrendingPosts trending;
  private final TrendsProperties props;
  private final boolean scheduling;

  private volatile boolean running;
  private volatile Thread thread;

  public TrendsWorker(
      TrendingPosts trending,
      TrendsProperties props,
      @Value("${short-link.scheduling.enabled:true}") boolean scheduling) {
    this.trending = trending;
    this.props = props;
    this.scheduling = scheduling;
  }

  @Override
  public void start() {
    running = true;
    if (props.enabled() && scheduling) {
      thread =
          Thread.ofPlatform()
              .name("kurl-trends")
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
        trending.refresh();
      } catch (RuntimeException failed) {
        log.warn("federation trends refresh failed reason={}", failed.toString());
      }
      try {
        Thread.sleep(props.every());
      } catch (InterruptedException stopping) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }
}
