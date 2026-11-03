package com.example.short_link.notification.application.push;

import java.util.Collection;

public interface PushSender {

  void send(Long recipientUserId, PushMessage message);

  void sendToAll(Collection<Long> recipientUserIds, PushMessage message);

  record PushMessage(
      String title,
      String subtitle,
      String body,
      String type,
      String shortCode,
      PushApp app,
      PushRoute route,
      PushActor actor,
      String threadId) {

    public PushMessage(String title, String subtitle, String body) {
      this(title, subtitle, body, null, null, PushApp.BLOG, null);
    }

    public PushMessage(
        String title, String subtitle, String body, String type, String shortCode, PushApp app) {
      this(title, subtitle, body, type, shortCode, app, null);
    }

    public PushMessage(
        String title,
        String subtitle,
        String body,
        String type,
        String shortCode,
        PushApp app,
        PushRoute route) {
      this(title, subtitle, body, type, shortCode, app, route, null, null);
    }
  }
}
