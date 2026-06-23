package com.example.short_link.notification.application.link;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.domain.LinkNotificationType;
import org.junit.jupiter.api.Test;

class LinkNotificationDispatcherTest {

  private final NotificationPreferenceService prefs = mock(NotificationPreferenceService.class);
  private final PushSender pushSender = mock(PushSender.class);
  private final LinkNotificationDispatcher dispatcher =
      new LinkNotificationDispatcher(prefs, pushSender);

  @Test
  void sendsWhenEnabled() {
    when(prefs.isEnabled(7L, LinkNotificationType.FIRST_CLICK)).thenReturn(true);
    dispatcher.dispatch(7L, LinkNotificationType.FIRST_CLICK, "/spring", "첫 클릭");
    verify(pushSender).send(eq(7L), any(PushSender.PushMessage.class));
  }

  @Test
  void skipsWhenDisabled() {
    when(prefs.isEnabled(7L, LinkNotificationType.MILESTONE)).thenReturn(false);
    dispatcher.dispatch(7L, LinkNotificationType.MILESTONE, "/spring", "100 클릭");
    verify(pushSender, never()).send(any(), any());
  }

  @Test
  void skipsWhenUserNull() {
    dispatcher.dispatch(null, LinkNotificationType.DIGEST, "x", "y");
    verify(pushSender, never()).send(any(), any());
  }
}
