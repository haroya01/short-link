package com.example.short_link.federation.scheduler;

import com.example.short_link.federation.application.trends.TrendingPosts;
import com.example.short_link.federation.application.trends.TrendsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

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
