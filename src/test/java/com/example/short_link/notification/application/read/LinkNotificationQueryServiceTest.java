package com.example.short_link.notification.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.dto.LinkNotificationListResult;
import com.example.short_link.notification.domain.LinkNotificationEntity;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.notification.domain.repository.LinkNotificationRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LinkNotificationQueryServiceTest {
  private static final long RECIPIENT = 9L;

  @Mock private LinkNotificationRepository repository;

  private LinkNotificationQueryService service() {
    return new LinkNotificationQueryService(repository);
  }

  private static LinkNotificationEntity entity(
      long id, LinkNotificationType type, String shortCode) {
    LinkNotificationEntity e =
        new LinkNotificationEntity(RECIPIENT, type, shortCode, "/" + shortCode, "본문");
    ReflectionTestUtils.setField(e, "id", id);
    ReflectionTestUtils.setField(e, "createdAt", Instant.parse("2026-06-25T00:00:00Z"));
    return e;
  }

  @Test
  void mapsRowsAndNoMoreWhenUnderLimit() {
    when(repository.findPageForRecipient(eq(RECIPIENT), isNull(), anyInt()))
        .thenReturn(List.of(entity(5L, LinkNotificationType.FIRST_CLICK, "spring")));

    LinkNotificationListResult result = service().list(RECIPIENT, null, 20);

    assertThat(result.hasMore()).isFalse();
    assertThat(result.nextCursor()).isNull();
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).shortCode()).isEqualTo("spring");
    assertThat(result.items().get(0).type()).isEqualTo(LinkNotificationType.FIRST_CLICK);
  }

  @Test
  void trimsToLimitAndSetsCursorWhenMore() {
    when(repository.findPageForRecipient(eq(RECIPIENT), isNull(), anyInt()))
        .thenReturn(
            List.of(
                entity(5L, LinkNotificationType.MILESTONE, "docs"),
                entity(4L, LinkNotificationType.EXPIRY_IMMINENT, "event7")));

    LinkNotificationListResult result = service().list(RECIPIENT, null, 1);

    assertThat(result.hasMore()).isTrue();
    assertThat(result.items()).hasSize(1);
    assertThat(result.nextCursor()).isEqualTo(5L);
  }

  @Test
  void requestsOneMoreThanLimit() {
    when(repository.findPageForRecipient(eq(RECIPIENT), isNull(), anyInt())).thenReturn(List.of());

    service().list(RECIPIENT, null, 20);

    ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
    verify(repository).findPageForRecipient(eq(RECIPIENT), isNull(), limit.capture());
    assertThat(limit.getValue()).isEqualTo(21);
  }

  @Test
  void unreadCountDelegates() {
    when(repository.countUnread(RECIPIENT)).thenReturn(7L);
    assertThat(service().unreadCount(RECIPIENT)).isEqualTo(7L);
  }
}
