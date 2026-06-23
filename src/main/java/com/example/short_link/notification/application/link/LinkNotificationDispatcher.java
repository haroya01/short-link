package com.example.short_link.notification.application.link;

import com.example.short_link.notification.application.push.PushSender;
import com.example.short_link.notification.domain.LinkNotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Single send point for link notifications — checks the owner's opt-out, then fires a push
 * (fire-and-forget via {@link PushSender}). Push-only by design: these don't live in the blog bell.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkNotificationDispatcher {

  private final NotificationPreferenceService preferences;
  private final PushSender pushSender;

  public void dispatch(Long userId, LinkNotificationType type, String subtitle, String body) {
    if (userId == null) {
      return;
    }
    if (!preferences.isEnabled(userId, type)) {
      return;
    }
    pushSender.send(userId, new PushSender.PushMessage("kurl", subtitle, body));
  }
}
