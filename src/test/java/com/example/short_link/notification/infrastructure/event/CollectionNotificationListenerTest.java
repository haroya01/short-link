package com.example.short_link.notification.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.short_link.common.event.CollectionConnectedEvent;
import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.write.RecordBlogNotificationUseCase;
import com.example.short_link.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectionNotificationListenerTest {

  private static final Instant AT = Instant.parse("2026-07-10T00:00:00Z");

  @Mock private RecordBlogNotificationUseCase recordUseCase;

  private CollectionNotificationListener listener() {
    return new CollectionNotificationListener(recordUseCase);
  }

  @Test
  void notifiesConnectedAuthorAndFansOutPathGrewToPriorContributors() {
    // Curator 2 wove work by author 5 into collection 42; 7 and 8 were already in the path.
    listener()
        .onCollectionConnected(
            new CollectionConnectedEvent(2L, 42L, "긴 여름의 독서", 10L, 5L, List.of(7L, 8L), AT));

    ArgumentCaptor<NotificationCollectionRef> connected =
        ArgumentCaptor.forClass(NotificationCollectionRef.class);
    verify(recordUseCase)
        .record(eq(5L), eq(NotificationType.CONNECTED), eq(2L), connected.capture());
    assertThat(connected.getValue().collectionId()).isEqualTo(42L);
    assertThat(connected.getValue().collectionName()).isEqualTo("긴 여름의 독서");
    assertThat(connected.getValue().postId()).isEqualTo(10L);

    ArgumentCaptor<NotificationCollectionRef> grew =
        ArgumentCaptor.forClass(NotificationCollectionRef.class);
    verify(recordUseCase)
        .recordForEach(eq(List.of(7L, 8L)), eq(NotificationType.PATH_GREW), eq(2L), grew.capture());
    assertThat(grew.getValue().collectionId()).isEqualTo(42L);
  }

  @Test
  void selfConnectSendsNoConnectedNoticeButStillGrowsThePath() {
    // The curator (2) wove their own work (author 2); no CONNECTED self-notice, but 7 still hears.
    listener()
        .onCollectionConnected(
            new CollectionConnectedEvent(2L, 42L, "c", 10L, 2L, List.of(7L), AT));

    verify(recordUseCase, never()).record(any(), eq(NotificationType.CONNECTED), any(), any());
    verify(recordUseCase)
        .recordForEach(eq(List.of(7L)), eq(NotificationType.PATH_GREW), eq(2L), any());
  }

  @Test
  void noConnectedAuthorSendsNoConnectedNotice() {
    // A connected note carries no author, so nobody is notified as the connected work's author.
    listener()
        .onCollectionConnected(
            new CollectionConnectedEvent(2L, 42L, "c", null, null, List.of(7L), AT));

    verify(recordUseCase, never()).record(any(), eq(NotificationType.CONNECTED), any(), any());
    verify(recordUseCase)
        .recordForEach(eq(List.of(7L)), eq(NotificationType.PATH_GREW), eq(2L), any());
  }

  @Test
  void emptyPathStillNotifiesConnectedAuthorAndFansOutNothing() {
    // Weaving into an empty (or solo-authored) path: the connected author still hears, but there
    // are no prior contributors to grow — recordForEach is a no-op on the empty list.
    listener()
        .onCollectionConnected(new CollectionConnectedEvent(2L, 42L, "c", 10L, 5L, List.of(), AT));

    verify(recordUseCase).record(eq(5L), eq(NotificationType.CONNECTED), eq(2L), any());
    verify(recordUseCase)
        .recordForEach(eq(List.of()), eq(NotificationType.PATH_GREW), eq(2L), any());
  }

  @Test
  void bothNoticesSharePayloadFromASingleEvent() {
    listener()
        .onCollectionConnected(
            new CollectionConnectedEvent(2L, 42L, "c", 10L, 5L, List.of(7L), AT));

    verify(recordUseCase).record(any(), eq(NotificationType.CONNECTED), any(), any());
    verify(recordUseCase).recordForEach(anyList(), eq(NotificationType.PATH_GREW), any(), any());
  }
}
