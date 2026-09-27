package com.example.short_link.link.health.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.health.domain.DestinationFailure;
import com.example.short_link.link.health.domain.LinkDestinationHealthEntity;
import com.example.short_link.link.health.domain.repository.LinkDestinationHealthRepository;
import com.example.short_link.link.health.domain.repository.LinkDestinationHealthRepository.DueDestination;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DestinationHealthRecorderTest {

  private static final DueDestination LINK =
      new DueDestination(7L, "abc1234", 42L, "https://shop.example.com/sale", null);
  private static final DestinationCheck NOT_FOUND =
      new DestinationCheck(DestinationCheck.Outcome.BROKEN, DestinationFailure.NOT_FOUND, 404);

  private final LinkDestinationHealthRepository healths =
      mock(LinkDestinationHealthRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final DestinationHealthRecorder recorder =
      new DestinationHealthRecorder(
          healths, events, new DestinationHealthProperties(true, 0, 0, 0));

  @Test
  void theEventGoesOutOnlyWhenTheBreakageIsConfirmed() {
    LinkDestinationHealthEntity[] stored = new LinkDestinationHealthEntity[1];
    when(healths.findById(7L)).thenAnswer(inv -> Optional.ofNullable(stored[0]));
    when(healths.save(any())).thenAnswer(inv -> stored[0] = inv.getArgument(0));

    recorder.record(LINK, NOT_FOUND, Instant.parse("2026-09-27T00:00:00Z"));
    verify(events, never()).publishEvent(any(Object.class));

    recorder.record(LINK, NOT_FOUND, Instant.parse("2026-09-28T00:00:00Z"));
    recorder.record(LINK, NOT_FOUND, Instant.parse("2026-09-29T00:00:00Z"));
    verify(events, times(1))
        .publishEvent(
            new DestinationBrokenEvent(
                42L, "abc1234", "/abc1234", DestinationFailure.NOT_FOUND, 404));
  }
}
