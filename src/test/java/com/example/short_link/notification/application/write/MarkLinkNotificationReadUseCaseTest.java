package com.example.short_link.notification.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.domain.repository.LinkNotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkLinkNotificationReadUseCaseTest {
  private static final Instant NOW = Instant.parse("2026-10-27T03:00:00Z");

  @Mock private LinkNotificationRepository repository;

  private MarkLinkNotificationReadUseCase useCase() {
    return new MarkLinkNotificationReadUseCase(repository, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void markReadStampsTheClockTimeOnTheRecipientsOwnNotice() {
    useCase().markRead(9L, 5L);
    verify(repository).markRead(5L, 9L, NOW);
  }

  @Test
  void markAllReadStampsTheClockTimeAndReturnsUpdatedCount() {
    when(repository.markAllRead(9L, NOW)).thenReturn(3);
    assertThat(useCase().markAllRead(9L)).isEqualTo(3);
  }
}
