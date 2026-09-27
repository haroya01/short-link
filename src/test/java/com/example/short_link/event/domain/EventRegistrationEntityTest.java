package com.example.short_link.event.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.event.exception.EventErrorCode;
import com.example.short_link.event.exception.EventException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventRegistrationEntityTest {

  private EventRegistrationEntity registration() {
    return new EventRegistrationEntity(1L, "Name", "user@example.com", "{}", "token-hash");
  }

  @Test
  void cancellationReportsOnlyTheFirstTransitionAndPreservesItsTimestamp() {
    EventRegistrationEntity registration = registration();
    Instant first = Instant.parse("2026-09-01T00:00:00Z");

    assertThat(registration.cancel(first)).isTrue();
    assertThat(registration.cancel(first.plusSeconds(60))).isFalse();

    assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CANCELED);
    assertThat(registration.getCanceledAt()).isEqualTo(first);
  }

  @Test
  void confirmedRegistrationCannotBeReactivatedAndHaveItsTokenReplaced() {
    EventRegistrationEntity registration = registration();

    assertThatThrownBy(() -> registration.reactivate("Changed", "changed", "new-token"))
        .isInstanceOfSatisfying(
            EventException.class,
            e -> assertThat(e.errorCode()).isEqualTo(EventErrorCode.ALREADY_REGISTERED));

    assertThat(registration.getName()).isEqualTo("Name");
    assertThat(registration.getAnswersJson()).isEqualTo("{}");
    assertThat(registration.getCancelTokenHash()).isEqualTo("token-hash");
  }

  @Test
  void rejectedReactivationPreservesCanceledRegistration() {
    EventRegistrationEntity registration = registration();
    Instant canceledAt = Instant.parse("2026-09-01T00:00:00Z");
    registration.cancel(canceledAt);

    assertThatThrownBy(() -> registration.reactivate(" ", "changed", "new-token"))
        .isInstanceOf(EventException.class);

    assertThat(registration.getName()).isEqualTo("Name");
    assertThat(registration.getAnswersJson()).isEqualTo("{}");
    assertThat(registration.getCancelTokenHash()).isEqualTo("token-hash");
    assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CANCELED);
    assertThat(registration.getCanceledAt()).isEqualTo(canceledAt);
  }

  @Test
  void constructionAndReactivationShareNameNormalizationAndLimits() {
    assertThatThrownBy(
            () -> new EventRegistrationEntity(1L, "x".repeat(101), "u@example.com", null, "hash"))
        .isInstanceOf(EventException.class);
    EventRegistrationEntity registration =
        new EventRegistrationEntity(1L, "  Original  ", "u@example.com", null, "hash");
    assertThat(registration.getName()).isEqualTo("Original");
    registration.cancel(Instant.parse("2026-09-01T00:00:00Z"));

    registration.reactivate("  New name  ", "new-answer", "new-hash");

    assertThat(registration.getName()).isEqualTo("New name");
    assertThat(registration.getCancelTokenHash()).isEqualTo("new-hash");
    assertThat(registration.isConfirmed()).isTrue();
    assertThat(registration.getCanceledAt()).isNull();
  }
}
