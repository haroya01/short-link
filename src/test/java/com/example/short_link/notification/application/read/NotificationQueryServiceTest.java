package com.example.short_link.notification.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.dto.NotificationListResult;
import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationActorReader;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

  private static final long RECIPIENT = 9L;

  @Mock private NotificationRepository repository;
  @Mock private NotificationActorReader actorReader;
  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  private NotificationQueryService service() {
    return new NotificationQueryService(repository, actorReader, jsonMapper);
  }

  private static NotificationEntity entity(
      long id, NotificationType type, Long actorId, String payload) {
    NotificationEntity e = new NotificationEntity(RECIPIENT, type, actorId, payload);
    ReflectionTestUtils.setField(e, "id", id);
    ReflectionTestUtils.setField(e, "createdAt", Instant.parse("2026-06-07T00:00:00Z"));
    return e;
  }

  @Test
  void mapsRowsResolvesActorsAndDecodesPostPayload() {
    NotificationEntity like =
        entity(5L, NotificationType.LIKE, 2L, "{\"postId\":10,\"slug\":\"s\",\"title\":\"t\"}");
    NotificationEntity follow = entity(4L, NotificationType.FOLLOW, 3L, null);
    when(repository.findPageForRecipient(eq(RECIPIENT), isNull(), anyInt()))
        .thenReturn(List.of(like, follow));
    when(actorReader.resolve(Set.of(2L, 3L)))
        .thenReturn(
            Map.of(
                2L, new NotificationActor(2L, "alice", "a.png"),
                3L, new NotificationActor(3L, "bob", null)));

    NotificationListResult result = service().list(RECIPIENT, null, 20);

    assertThat(result.hasMore()).isFalse();
    assertThat(result.nextCursor()).isNull();
    assertThat(result.items()).hasSize(2);
    var first = result.items().get(0);
    assertThat(first.type()).isEqualTo(NotificationType.LIKE);
    assertThat(first.actor().username()).isEqualTo("alice");
    assertThat(first.post().slug()).isEqualTo("s");
    assertThat(first.post().title()).isEqualTo("t");
    assertThat(first.read()).isFalse();
    var second = result.items().get(1);
    assertThat(second.type()).isEqualTo(NotificationType.FOLLOW);
    assertThat(second.post()).isNull();
    assertThat(second.actor().username()).isEqualTo("bob");
  }

  @Test
  void decodesCollectionPayloadForGraphNoticesAndLeavesPostNull() {
    NotificationEntity connected =
        entity(
            6L,
            NotificationType.CONNECTED,
            2L,
            "{\"collectionId\":42,\"collectionName\":\"긴 여름의 독서\",\"postId\":10}");
    NotificationEntity pathGrew =
        entity(
            7L,
            NotificationType.PATH_GREW,
            3L,
            "{\"collectionId\":42,\"collectionName\":\"긴 여름의 독서\",\"postId\":null}");
    when(repository.findPageForRecipient(eq(RECIPIENT), isNull(), anyInt()))
        .thenReturn(List.of(connected, pathGrew));
    when(actorReader.resolve(Set.of(2L, 3L)))
        .thenReturn(
            Map.of(
                2L, new NotificationActor(2L, "alice", null),
                3L, new NotificationActor(3L, "bob", null)));

    NotificationListResult result = service().list(RECIPIENT, null, 20);

    var first = result.items().get(0);
    assertThat(first.type()).isEqualTo(NotificationType.CONNECTED);
    assertThat(first.collection().collectionId()).isEqualTo(42L);
    assertThat(first.collection().collectionName()).isEqualTo("긴 여름의 독서");
    assertThat(first.collection().postId()).isEqualTo(10L);
    assertThat(first.post()).isNull();
    assertThat(first.series()).isNull();
    var second = result.items().get(1);
    assertThat(second.type()).isEqualTo(NotificationType.PATH_GREW);
    assertThat(second.collection().collectionId()).isEqualTo(42L);
    assertThat(second.collection().postId()).isNull();
    assertThat(second.post()).isNull();
  }

  @Test
  void overFetchesByOneAndExposesCursorWhenMorePagesExist() {
    NotificationEntity a = entity(5L, NotificationType.FOLLOW, 2L, null);
    NotificationEntity b = entity(4L, NotificationType.FOLLOW, 2L, null);
    // limit 1 ⇒ fetch 2; two rows back means a further page, page trimmed to the first.
    when(repository.findPageForRecipient(eq(RECIPIENT), isNull(), eq(2))).thenReturn(List.of(a, b));
    when(actorReader.resolve(Set.of(2L)))
        .thenReturn(Map.of(2L, new NotificationActor(2L, "alice", null)));

    NotificationListResult result = service().list(RECIPIENT, null, 1);

    assertThat(result.items()).hasSize(1);
    assertThat(result.hasMore()).isTrue();
    assertThat(result.nextCursor()).isEqualTo(5L);
  }

  @Test
  void clampsLimitIntoBounds() {
    when(repository.findPageForRecipient(eq(RECIPIENT), isNull(), anyInt())).thenReturn(List.of());
    ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);

    service().list(RECIPIENT, null, 0); // below min ⇒ clamped to 1, fetch 1 + 1
    service().list(RECIPIENT, null, 999); // above max ⇒ clamped to 50, fetch 50 + 1

    verify(repository, org.mockito.Mockito.times(2))
        .findPageForRecipient(eq(RECIPIENT), isNull(), limit.capture());
    assertThat(limit.getAllValues()).containsExactly(2, 51);
  }

  @Test
  void deletedActorLeavesNullActorOnView() {
    NotificationEntity like = entity(5L, NotificationType.LIKE, 2L, "{\"postId\":1}");
    when(repository.findPageForRecipient(eq(RECIPIENT), isNull(), anyInt()))
        .thenReturn(List.of(like));
    when(actorReader.resolve(Set.of(2L))).thenReturn(Map.of()); // actor since deleted

    NotificationListResult result = service().list(RECIPIENT, null, 20);

    assertThat(result.items().get(0).actor()).isNull();
  }

  @Test
  void unreadCountDelegates() {
    when(repository.countUnread(RECIPIENT)).thenReturn(4L);

    assertThat(service().unreadCount(RECIPIENT)).isEqualTo(4L);
  }
}
