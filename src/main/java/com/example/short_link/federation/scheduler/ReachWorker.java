package com.example.short_link.federation.scheduler;

import com.example.short_link.federation.application.trends.LatestProperties;
import com.example.short_link.federation.application.trends.ReachProperties;
import com.example.short_link.federation.application.trends.RemoteReach;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReachWorker implements SmartLifecycle {

  private final RemoteReach reach;
  private final ReachProperties props;
  private final LatestProperties latestProps;
  private final boolean scheduling;

  private volatile boolean running;
  private volatile Thread thread;

  public ReachWorker(
      RemoteReach reach,
      ReachProperties props,
      LatestProperties latestProps,
      @Value("${short-link.scheduling.enabled:true}") boolean scheduling) {
    this.reach = reach;
    this.props = props;
    this.latestProps = latestProps;
    this.scheduling = scheduling;
  }

  @Override
  public void start() {
    running = true;
    if (props.enabled() && latestProps.pulling() && scheduling) {
      thread =
          Thread.ofPlatform()
              .name("kurl-reach")
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
        int counted = reach.round();
        log.info(
            "federation reach round counted={} tookMs={}",
            counted,
            (System.nanoTime() - started) / 1_000_000);
      } catch (RuntimeException failed) {
        log.warn("federation reach round failed reason={}", failed.toString());
      }
    }
  }
}
