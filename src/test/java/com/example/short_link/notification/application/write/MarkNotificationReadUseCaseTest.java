package com.example.short_link.notification.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkNotificationReadUseCaseTest {

  @Mock private NotificationRepository repository;

  private MarkNotificationReadUseCase useCase() {
    return new MarkNotificationReadUseCase(repository);
  }

  @Test
  void markReadDelegatesWithOwnership() {
    useCase().markRead(9L, 5L);

    verify(repository).markRead(eq(5L), eq(9L), any(Instant.class));
  }

  @Test
  void markAllReadReturnsUpdatedCount() {
    when(repository.markAllRead(eq(9L), any(Instant.class))).thenReturn(3);

    assertThat(useCase().markAllRead(9L)).isEqualTo(3);
  }
}
