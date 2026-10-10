package com.example.short_link.notification.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.NotificationTargetCodec;
import com.example.short_link.notification.application.dto.NotificationListResult;
import com.example.short_link.notification.domain.NotificationActor;
import com.example.short_link.notification.domain.NotificationEntity;
import com.example.short_link.notification.domain.NotificationGroup;
import com.example.short_link.notification.domain.NotificationGroupActor;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationActorReader;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
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
    return new NotificationQueryService(
        repository, actorReader, new NotificationTargetCodec(jsonMapper));
  }

  private static NotificationEntity entity(
      long id, NotificationType type, Long actorId, String payload) {
    NotificationEntity e = new NotificationEntity(RECIPIENT, type, actorId, payload);
    ReflectionTestUtils.setField(e, "id", id);
    ReflectionTestUtils.setField(e, "createdAt", Instant.parse("2026-06-07T00:00:00Z"));
    return e;
  }

  private void stubSingles(int fetch, NotificationEntity... rows) {
    List<NotificationGroup> groups =
        Arrays.stream(rows).map(row -> new NotificationGroup(row, 1, !row.isRead())).toList();
    if (fetch < 0) {
      when(repository.findGroupPage(eq(RECIPIENT), isNull(), anyInt())).thenReturn(groups);
    } else {
      when(repository.findGroupPage(eq(RECIPIENT), isNull(), eq(fetch))).thenReturn(groups);
    }
  }

  private static NotificationEntity remoteEntity(
      long id, NotificationType type, Long remoteId, String payload, String groupKey) {
    NotificationEntity e =
        new NotificationEntity(RECIPIENT, type, null, remoteId, payload, groupKey);
    ReflectionTestUtils.setField(e, "id", id);
    ReflectionTestUtils.setField(e, "createdAt", Instant.parse("2026-10-07T00:00:00Z"));
    return e;
  }

  @Test
  void aGroupShowsItsNewestMemberItsSizeAndItsNewestThreeActorsFromAnyServer() {
    String key = "NOTE_LIKE:5:2026-10-07";
    NotificationEntity newest =
        remoteEntity(30L, NotificationType.NOTE_LIKE, 7L, "{\"noteId\":5,\"excerpt\":\"hi\"}", key);
    NotificationEntity reply =
        entity(
            20L,
            NotificationType.NOTE_REPLY,
            3L,
            "{\"noteId\":5,\"excerpt\":\"hi\",\"sourceNoteId\":9,\"sourceExcerpt\":\"me too\"}");
    when(repository.findGroupPage(eq(RECIPIENT), isNull(), anyInt()))
        .thenReturn(
            List.of(
                new NotificationGroup(newest, 4, true), new NotificationGroup(reply, 1, false)));
    when(repository.recentActors(RECIPIENT, List.of(key), 3))
        .thenReturn(
            List.of(
                new NotificationGroupActor(key, null, 7L),
                new NotificationGroupActor(key, 2L, null),
                new NotificationGroupActor(key, 3L, null)));
    when(actorReader.resolve(Set.of(2L, 3L)))
        .thenReturn(
            Map.of(
                2L, new NotificationActor(2L, "bob", null),
                3L, new NotificationActor(3L, "carol", null)));
    when(actorReader.resolveRemote(Set.of(7L)))
        .thenReturn(
            Map.of(
                7L,
                new NotificationActor(
                    null, "alice@mastodon.social", null, "https://mastodon.social/@alice")));

    NotificationListResult result = service().list(RECIPIENT, null, 20);

    var likes = result.items().get(0);
    assertThat(likes.id()).isEqualTo(30L);
    assertThat(likes.count()).isEqualTo(4);
    assertThat(likes.read()).isFalse();
    assertThat(likes.actor().profileUrl()).isEqualTo("https://mastodon.social/@alice");
    assertThat(likes.actors())
        .extracting(NotificationActor::username)
        .containsExactly("alice@mastodon.social", "bob", "carol");
    assertThat(likes.note().noteId()).isEqualTo(5L);
    var replied = result.items().get(1);
    assertThat(replied.count()).isEqualTo(1);
    assertThat(replied.read()).isTrue();
    assertThat(replied.actors()).extracting(NotificationActor::username).containsExactly("carol");
    assertThat(replied.note().sourceNoteId()).isEqualTo(9L);
    assertThat(replied.note().sourceExcerpt()).isEqualTo("me too");
  }

  @Test
  void mapsRowsResolvesActorsAndDecodesPostPayload() {
    NotificationEntity like =
        entity(5L, NotificationType.LIKE, 2L, "{\"postId\":10,\"slug\":\"s\",\"title\":\"t\"}");
    NotificationEntity follow = entity(4L, NotificationType.FOLLOW, 3L, null);
    stubSingles(-1, like, follow);
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
    stubSingles(-1, connected, pathGrew);
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
    stubSingles(2, a, b);
    when(actorReader.resolve(Set.of(2L)))
        .thenReturn(Map.of(2L, new NotificationActor(2L, "alice", null)));

    NotificationListResult result = service().list(RECIPIENT, null, 1);

    assertThat(result.items()).hasSize(1);
    assertThat(result.hasMore()).isTrue();
    assertThat(result.nextCursor()).isEqualTo(5L);
  }

  @Test
  void clampsLimitIntoBounds() {
    stubSingles(-1);
    ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);

    service().list(RECIPIENT, null, 0); // below min ⇒ clamped to 1, fetch 1 + 1
    service().list(RECIPIENT, null, 999); // above max ⇒ clamped to 50, fetch 50 + 1

    verify(repository, org.mockito.Mockito.times(2))
        .findGroupPage(eq(RECIPIENT), isNull(), limit.capture());
    assertThat(limit.getAllValues()).containsExactly(2, 51);
  }

  @Test
  void deletedActorLeavesNullActorOnView() {
    NotificationEntity like = entity(5L, NotificationType.LIKE, 2L, "{\"postId\":1}");
    stubSingles(-1, like);
    when(actorReader.resolve(Set.of(2L))).thenReturn(Map.of());

    NotificationListResult result = service().list(RECIPIENT, null, 20);

    assertThat(result.items().get(0).actor()).isNull();
  }

  @Test
  void mentionsAskOnlyForMentionsAndRepliesToTheReadersWriting() {
    NotificationEntity reply =
        entity(
            8L,
            NotificationType.NOTE_REPLY,
            2L,
            "{\"noteId\":5,\"excerpt\":\"hi\",\"sourceNoteId\":9,\"sourceExcerpt\":\"me too\"}");
    when(repository.findGroupPageOfTypes(
            eq(RECIPIENT),
            eq(
                EnumSet.of(
                    NotificationType.MENTION,
                    NotificationType.NOTE_MENTION,
                    NotificationType.REPLY,
                    NotificationType.NOTE_REPLY,
                    NotificationType.COMMENT)),
            eq(40L),
            eq(3)))
        .thenReturn(
            List.of(
                new NotificationGroup(reply, 1, true),
                new NotificationGroup(reply, 1, true),
                new NotificationGroup(reply, 1, true)));
    when(actorReader.resolve(Set.of(2L)))
        .thenReturn(Map.of(2L, new NotificationActor(2L, "bob", null)));

    NotificationListResult result = service().mentions(RECIPIENT, 40L, 2);

    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).type()).isEqualTo(NotificationType.NOTE_REPLY);
    assertThat(result.hasMore()).isTrue();
    assertThat(result.nextCursor()).isEqualTo(8L);
  }

  @Test
  void unreadCountDelegates() {
    when(repository.countUnread(RECIPIENT)).thenReturn(4L);

    assertThat(service().unreadCount(RECIPIENT)).isEqualTo(4L);
  }
}
