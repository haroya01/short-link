package com.example.short_link.link.health.scheduler;

import com.example.short_link.common.lock.RedisDistributedLock;
import com.example.short_link.link.health.application.DestinationCheck;
import com.example.short_link.link.health.application.DestinationHealthProperties;
import com.example.short_link.link.health.application.DestinationHealthRecorder;
import com.example.short_link.link.health.application.DestinationProbe;
import com.example.short_link.link.health.domain.repository.LinkDestinationHealthRepository;
import com.example.short_link.link.health.domain.repository.LinkDestinationHealthRepository.DueDestination;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DestinationHealthJob {

  private static final String LOCK_KEY = "kurl:destination-health";

  private final LinkDestinationHealthRepository healths;
  private final DestinationProbe probe;
  private final DestinationHealthRecorder recorder;
  private final DestinationHealthProperties props;
  private final RedisDistributedLock lock;
  private final MeterRegistry meterRegistry;
  private final Clock clock;

  @Scheduled(cron = "${short-link.destination-health.cron:0 17 * * * *}", zone = "Asia/Seoul")
  public void run() {
    if (!props.enabled()) return;
    if (!lock.tryAcquire(LOCK_KEY, Duration.ofMinutes(30))) {
      log.debug("destination health skipped — lock held");
      return;
    }
    try {
      Instant now = clock.instant();
      List<DueDestination> due =
          healths.findDue(
              now, now.minus(Duration.ofHours(props.recheckHours())), props.batchSize());
      for (DueDestination link : due) {
        DestinationCheck check = probe.check(link.originalUrl());
        recorder.record(link, check, clock.instant());
        meterRegistry
            .counter(
                "short_link.destination_health",
                "outcome",
                check.outcome().name().toLowerCase(Locale.ROOT))
            .increment();
      }
      log.info("destination health: checked {} links", due.size());
    } finally {
      lock.release(LOCK_KEY);
    }
  }
}
