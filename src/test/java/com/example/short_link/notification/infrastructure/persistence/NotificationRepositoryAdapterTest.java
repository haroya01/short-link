package com.example.short_link.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRepositoryAdapterTest {

  @Mock private JpaNotificationRepository jpa;

  private NotificationRepositoryAdapter adapter() {
    return new NotificationRepositoryAdapter(jpa);
  }

  @Test
  void delegatesSaveAndMarkAll() {
    NotificationEntity entity = new NotificationEntity(9L, NotificationType.FOLLOW, 2L, null);
    when(jpa.save(entity)).thenReturn(entity);
    when(jpa.markAllRead(eq(9L), any(Instant.class))).thenReturn(2);

    assertThat(adapter().save(entity)).isSameAs(entity);
    assertThat(adapter().markAllRead(9L, Instant.now())).isEqualTo(2);
    assertThat(adapter().recentActors(9L, List.of(), 3)).isEmpty();
  }
}
