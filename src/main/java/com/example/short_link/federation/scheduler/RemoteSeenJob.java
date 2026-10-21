package com.example.short_link.federation.scheduler;

import com.example.short_link.federation.application.trends.RemoteSeen;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemoteSeenJob {

  private final RemoteSeen seen;

  @Scheduled(fixedDelayString = "${short-link.federation.seen.flush-every:PT5S}")
  public void flush() {
    seen.flush();
  }

  @Scheduled(cron = "${short-link.federation.seen.cleanup-cron:0 35 4 * * *}", zone = "Asia/Seoul")
  public void sweep() {
    long started = System.nanoTime();
    int deleted = seen.sweep();
    log.info(
        "federation seen cleanup deleted={} tookMs={}",
        deleted,
        (System.nanoTime() - started) / 1_000_000);
  }
}
