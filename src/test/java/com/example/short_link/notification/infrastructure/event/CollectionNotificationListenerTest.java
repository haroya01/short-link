package com.example.short_link.notification.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.short_link.common.event.CollectionConnectedEvent;
import com.example.short_link.notification.application.dto.NotificationCollectionRef;
import com.example.short_link.notification.application.push.PushRoute;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.infrastructure.event.StoredNotices.Push;
import com.example.short_link.notification.infrastructure.event.StoredNotices.Row;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollectionNotificationListenerTest {

  private static final Instant AT = Instant.parse("2026-07-10T00:00:00Z");

  private final StoredNotices stored = new StoredNotices();

  private CollectionNotificationListener listener() {
    return new CollectionNotificationListener(stored.useCase(), stored.blocks);
  }

  @Test
  void aCuratorTheAuthorOrAContributorSilencedIsNotHeardFrom() {
    stored.silence(5L, 2L);
    stored.silence(8L, 2L);

    listener()
        .onCollectionConnected(
            new CollectionConnectedEvent(2L, 42L, "c", 10L, 5L, List.of(7L, 8L), AT));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                7L,
                NotificationType.PATH_GREW,
                2L,
                null,
                new NotificationCollectionRef(42L, "c", 10L),
                null));
    assertThat(stored.silenceChecks()).isEqualTo(3);
  }

  @Test
  void notifiesConnectedAuthorAndFansOutPathGrewToPriorContributors() {
    stored.user(2L, "curator").user(5L, "author").user(7L, "a").user(8L, "b");

    listener()
        .onCollectionConnected(
            new CollectionConnectedEvent(2L, 42L, "긴 여름의 독서", 10L, 5L, List.of(7L, 8L), AT));

    NotificationCollectionRef collection = new NotificationCollectionRef(42L, "긴 여름의 독서", 10L);
    assertThat(stored.rows())
        .containsExactly(
            new Row(5L, NotificationType.CONNECTED, 2L, null, collection, null),
            new Row(7L, NotificationType.PATH_GREW, 2L, null, collection, null),
            new Row(8L, NotificationType.PATH_GREW, 2L, null, collection, null));
    PushRoute toTheCollection = new PushRoute("curator", null, null, null, 42L, null, null);
    assertThat(stored.pushes())
        .containsExactly(
            new Push(List.of(5L), "CONNECTED", toTheCollection),
            new Push(List.of(7L, 8L), "PATH_GREW", toTheCollection));
  }

  @Test
  void selfConnectSendsNoConnectedNoticeButStillGrowsThePath() {
    listener()
        .onCollectionConnected(
            new CollectionConnectedEvent(2L, 42L, "c", 10L, 2L, List.of(7L), AT));

    assertThat(stored.rows())
        .extracting(Row::recipient, Row::type)
        .containsExactly(tuple(7L, NotificationType.PATH_GREW));
  }

  @Test
  void noConnectedAuthorSendsNoConnectedNotice() {
    listener()
        .onCollectionConnected(
            new CollectionConnectedEvent(2L, 42L, "c", null, null, List.of(7L), AT));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                7L,
                NotificationType.PATH_GREW,
                2L,
                null,
                new NotificationCollectionRef(42L, "c", null),
                null));
  }

  @Test
  void emptyPathStillNotifiesConnectedAuthorAndFansOutNothing() {
    listener()
        .onCollectionConnected(new CollectionConnectedEvent(2L, 42L, "c", 10L, 5L, List.of(), AT));

    assertThat(stored.rows())
        .containsExactly(
            new Row(
                5L,
                NotificationType.CONNECTED,
                2L,
                null,
                new NotificationCollectionRef(42L, "c", 10L),
                null));
  }
}
