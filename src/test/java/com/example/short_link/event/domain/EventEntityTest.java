package com.example.short_link.event.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.event.exception.EventException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class EventEntityTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

  private EventEntity event(Integer capacity, Instant closeAt) {
    return new EventEntity(
        1L,
        "slug123abc",
        "스터디 모집",
        null,
        NOW.plus(7, ChronoUnit.DAYS),
        null,
        "Asia/Tokyo",
        "시부야",
        null,
        null,
        capacity,
        closeAt,
        ContactField.EMAIL);
  }

  @Test
  void acceptsRegistrations_whenOpenAndBeforeClose() {
    assertThat(event(null, null).acceptsRegistrations(NOW)).isTrue();
    assertThat(event(10, NOW.plusSeconds(60)).acceptsRegistrations(NOW)).isTrue();
  }

  @Test
  void rejectsRegistrations_afterCloseAt() {
    assertThat(event(null, NOW.minusSeconds(1)).acceptsRegistrations(NOW)).isFalse();
    assertThat(event(null, NOW).acceptsRegistrations(NOW)).isFalse();
  }

  @Test
  void rejectsRegistrations_whenClosedOrCanceled() {
    EventEntity closed = event(null, null);
    closed.close();
    assertThat(closed.acceptsRegistrations(NOW)).isFalse();

    EventEntity canceled = event(null, null);
    canceled.cancel();
    assertThat(canceled.acceptsRegistrations(NOW)).isFalse();
  }

  @Test
  void spotsLeft_nullWithoutCapacity() {
    assertThat(event(null, null).spotsLeft()).isNull();
    assertThat(event(5, null).spotsLeft()).isEqualTo(5);
  }

  @Test
  void canceledEvent_rejectsMutation() {
    EventEntity event = event(null, null);
    event.cancel();
    assertThatThrownBy(event::close).isInstanceOf(EventException.class);
    assertThatThrownBy(event::reopen).isInstanceOf(EventException.class);
  }

  @Test
  void effectiveEnd_fallsBackToStartsAt() {
    EventEntity event = event(null, null);
    assertThat(event.effectiveEnd()).isEqualTo(event.getStartsAt());
  }
}
