package com.example.short_link.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.domain.LinkNotificationEntity;
import com.example.short_link.notification.domain.LinkNotificationType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class LinkNotificationRepositoryAdapterTest {
  @Mock private JpaLinkNotificationRepository jpa;

  private LinkNotificationRepositoryAdapter adapter() {
    return new LinkNotificationRepositoryAdapter(jpa);
  }

  @Test
  void firstPageUsesNewestFirstQueryWithoutCursor() {
    when(jpa.findByRecipientUserIdOrderByIdDesc(eq(9L), any(Pageable.class))).thenReturn(List.of());
    adapter().findPageForRecipient(9L, null, 5);
    verify(jpa).findByRecipientUserIdOrderByIdDesc(9L, PageRequest.of(0, 5));
  }

  @Test
  void cursorPageUsesIdLessThanQuery() {
    when(jpa.findByRecipientUserIdAndIdLessThanOrderByIdDesc(eq(9L), eq(100L), any(Pageable.class)))
        .thenReturn(List.of());
    adapter().findPageForRecipient(9L, 100L, 5);
    verify(jpa).findByRecipientUserIdAndIdLessThanOrderByIdDesc(9L, 100L, PageRequest.of(0, 5));
  }

  @Test
  void delegatesSaveUnreadAndMarks() {
    LinkNotificationEntity entity =
        new LinkNotificationEntity(
            9L, LinkNotificationType.FIRST_CLICK, "spring", "/spring", "첫 클릭");
    when(jpa.countByRecipientUserIdAndReadAtIsNull(9L)).thenReturn(3L);
    when(jpa.save(entity)).thenReturn(entity);
    when(jpa.markAllRead(eq(9L), any(Instant.class))).thenReturn(2);

    assertThat(adapter().countUnread(9L)).isEqualTo(3L);
    assertThat(adapter().save(entity)).isSameAs(entity);
    assertThat(adapter().markAllRead(9L, Instant.now())).isEqualTo(2);

    adapter().markRead(5L, 9L, Instant.parse("2026-06-25T00:00:00Z"));
    verify(jpa).markRead(eq(5L), eq(9L), any(Instant.class));
  }
}
