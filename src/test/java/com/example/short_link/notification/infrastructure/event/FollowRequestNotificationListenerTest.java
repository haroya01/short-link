package com.example.short_link.notification.infrastructure.event;

import static org.mockito.Mockito.verify;

import com.example.short_link.common.event.AccountUnlockedEvent;
import com.example.short_link.common.event.FollowRequestSettledEvent;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FollowRequestNotificationListenerTest {

  @Mock private NotificationRepository notifications;

  @Test
  void aSettledRequestTakesItsNotificationWithIt() {
    new FollowRequestNotificationListener(notifications)
        .onSettled(new FollowRequestSettledEvent(2L, null, 7L));

    verify(notifications).deleteFollowRequest(2L, null, 7L);
  }

  @Test
  void unlockingClearsEveryRequestNotification() {
    new FollowRequestNotificationListener(notifications).onUnlocked(new AccountUnlockedEvent(2L));

    verify(notifications).deleteFollowRequests(2L);
  }
}
