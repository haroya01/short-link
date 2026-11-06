package com.example.short_link.link.health.application;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.health.domain.LinkDestinationHealthEntity;
import com.example.short_link.link.health.domain.repository.LinkDestinationHealthRepository;
import com.example.short_link.link.health.domain.repository.LinkDestinationHealthRepository.DueDestination;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DestinationHealthRecorder {

  private final LinkDestinationHealthRepository healths;
  private final ApplicationEventPublisher events;
  private final DestinationHealthProperties props;
  private final LinkCacheEviction linkCache;

  @Transactional
  public void record(DueDestination link, DestinationCheck check, Instant now) {
    LinkDestinationHealthEntity health =
        healths
            .findById(link.linkId())
            .orElseGet(() -> new LinkDestinationHealthEntity(new LinkId(link.linkId())));
    Instant goneSince = health.goneSince();
    health.startCheckOf(link.originalUrl());
    switch (check.outcome()) {
      case HEALTHY -> health.markHealthy(now);
      case INCONCLUSIVE -> health.markInconclusive(now);
      case BROKEN -> {
        if (health.markFailed(check.failure(), check.httpStatus(), now, props.confirmAfter())) {
          events.publishEvent(
              new DestinationBrokenEvent(
                  link.userId(),
                  link.shortCode(),
                  label(link),
                  check.failure(),
                  check.httpStatus()));
        }
      }
    }
    healths.save(health);
    if (!Objects.equals(goneSince, health.goneSince())) {
      linkCache.evictAfterCommit(ShortCode.of(link.shortCode()));
    }
  }

  private static String label(DueDestination link) {
    return link.note() != null && !link.note().isBlank() ? link.note() : "/" + link.shortCode();
  }
}
