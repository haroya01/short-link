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

  @Test
  void effectiveEnd_prefersEndsAt() {
    Instant ends = NOW.plus(8, ChronoUnit.DAYS);
    EventEntity event =
        new EventEntity(
            1L,
            "slug123abc",
            "스터디",
            null,
            NOW.plus(7, ChronoUnit.DAYS),
            ends,
            "Asia/Seoul",
            null,
            null,
            null,
            null,
            null,
            ContactField.EMAIL);
    assertThat(event.effectiveEnd()).isEqualTo(ends);
  }

  @Test
  void update_rewritesEditableFields() {
    EventEntity event = event(null, null);
    Instant starts = NOW.plus(10, ChronoUnit.DAYS);

    event.update(
        "새 제목",
        "설명",
        starts,
        starts.plusSeconds(3600),
        "Asia/Seoul",
        "강남",
        "https://map",
        "https://meet",
        30,
        starts.minusSeconds(60));

    assertThat(event.getTitle()).isEqualTo("새 제목");
    assertThat(event.getDescriptionMd()).isEqualTo("설명");
    assertThat(event.getStartsAt()).isEqualTo(starts);
    assertThat(event.getTimezone()).isEqualTo("Asia/Seoul");
    assertThat(event.getLocationText()).isEqualTo("강남");
    assertThat(event.getCapacity()).isEqualTo(30);
  }

  @Test
  void canceledEvent_rejectsUpdateAndCover() {
    EventEntity event = event(null, null);
    event.cancel();
    assertThatThrownBy(
            () -> event.update("제목", null, NOW, null, "Asia/Seoul", null, null, null, null, null))
        .isInstanceOf(EventException.class);
    assertThatThrownBy(() -> event.updateCoverImage("event-covers/1/1/a.jpg"))
        .isInstanceOf(EventException.class);
  }

  @Test
  void reopen_restoresRegistration() {
    EventEntity event = event(null, null);
    event.close();
    event.reopen();
    assertThat(event.acceptsRegistrations(NOW)).isTrue();
  }

  @Test
  void coverAndPrimaryLink_areAttachable() {
    EventEntity event = event(null, null);
    event.updateCoverImage("event-covers/1/1/a.jpg");
    event.attachPrimaryLink(7L);
    event.markPiiPurged(NOW);
    assertThat(event.getCoverImageKey()).isEqualTo("event-covers/1/1/a.jpg");
    assertThat(event.getPrimaryLinkId()).isEqualTo(7L);
    assertThat(event.getPiiPurgedAt()).isEqualTo(NOW);
  }

  @Test
  void ownership_isExactUserMatch() {
    EventEntity event = event(null, null);
    assertThat(event.isOwnedBy(1L)).isTrue();
    assertThat(event.isOwnedBy(2L)).isFalse();
  }
}
