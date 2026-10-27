package com.example.short_link.notification.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.example.short_link.common.event.AccountUnlockedEvent;
import com.example.short_link.common.event.FollowRequestSettledEvent;
import com.example.short_link.common.event.FollowRequestedEvent;
import com.example.short_link.common.event.RemoteFollowedEvent;
import com.example.short_link.notification.domain.NotificationType;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import com.example.short_link.notification.infrastructure.event.StoredNotices.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FollowNotificationListenerTest {

  @Mock private NotificationRepository notifications;

  private final StoredNotices stored = new StoredNotices();

  private NoteNotificationListener listener() {
    return new NoteNotificationListener(stored.useCase(), stored.blocks, null);
  }

  @Test
  void aFollowFromAnotherServerIsItsOwnNotice() {
    listener().onRemoteFollowed(new RemoteFollowedEvent(9L, 7L));

    assertThat(stored.rows())
        .containsExactly(new Row(9L, NotificationType.REMOTE_FOLLOW, null, 7L, null, null));
    assertThat(stored.silenceChecks()).isEqualTo(1);
  }

  @Test
  void aFollowFromAnAccountTheMemberSilencedIsNoNotice() {
    stored.silenceRemote(9L, 7L);

    listener().onRemoteFollowed(new RemoteFollowedEvent(9L, 7L));

    assertThat(stored.rows()).isEmpty();
  }

  @Test
  void aFollowRequestNotifiesTheLockedMemberUnlessTheyMutedTheAsker() {
    stored.silence(2L, 3L);

    listener().onFollowRequested(new FollowRequestedEvent(2L, null, 7L));
    listener().onFollowRequested(new FollowRequestedEvent(2L, 3L, null));

    assertThat(stored.rows())
        .containsExactly(new Row(2L, NotificationType.FOLLOW_REQUEST, null, 7L, null, null));
    assertThat(stored.silenceChecks()).isEqualTo(2);
  }

  @Test
  void aSettledRequestTakesItsNotificationWithIt() {
    new FollowNotificationListener(notifications)
        .onSettled(new FollowRequestSettledEvent(2L, null, 7L));

    verify(notifications).deleteFollowRequest(2L, null, 7L);
  }

  @Test
  void unlockingClearsEveryRequestNotification() {
    new FollowNotificationListener(notifications).onUnlocked(new AccountUnlockedEvent(2L));

    verify(notifications).deleteFollowRequests(2L);
  }
}
