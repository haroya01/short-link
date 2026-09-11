package com.example.short_link.notification.application.push;

import com.example.short_link.common.transaction.AfterCommit;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Submits the optional push mirror after the in-app notification commits, for any transport. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushDelivery {
  private final PushSender pushSender;

  public void send(Long recipientUserId, PushSender.PushMessage message) {
    AfterCommit.run(() -> deliver(() -> pushSender.send(recipientUserId, message)));
  }

  public void sendToAll(Collection<Long> recipientUserIds, PushSender.PushMessage message) {
    List<Long> recipients = List.copyOf(recipientUserIds);
    AfterCommit.run(() -> deliver(() -> pushSender.sendToAll(recipients, message)));
  }

  private void deliver(Runnable delivery) {
    try {
      delivery.run();
    } catch (RuntimeException e) {
      log.warn("notification push submission failed: {}", e.toString());
    }
  }
}
