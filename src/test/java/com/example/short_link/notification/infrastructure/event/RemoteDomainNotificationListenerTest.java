package com.example.short_link.notification.infrastructure.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.short_link.common.event.RemoteAccountBlockedEvent;
import com.example.short_link.common.event.RemoteDomainBlockedEvent;
import com.example.short_link.common.event.ServerSuspendedEvent;
import com.example.short_link.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemoteDomainNotificationListenerTest {

  @Mock private NotificationRepository notifications;

  @Test
  void blockingAnAccountClearsOnlyThatMembersNoticesFromIt() {
    new RemoteDomainNotificationListener(notifications)
        .onAccountBlocked(new RemoteAccountBlockedEvent(7L, 42L));

    verify(notifications).deleteFromRemoteActor(7L, 42L);
    verifyNoMoreInteractions(notifications);
  }

  @Test
  void blockingOrSuspendingAServerClearsItsNotices() {
    RemoteDomainNotificationListener listener = new RemoteDomainNotificationListener(notifications);

    listener.onDomainBlocked(new RemoteDomainBlockedEvent(7L, "spam.example"));
    listener.onServerSuspended(new ServerSuspendedEvent("gone.example"));

    verify(notifications).deleteFromDomain(7L, "spam.example");
    verify(notifications).deleteFromServer("gone.example");
  }
}
