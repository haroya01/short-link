package com.example.short_link.notification.application.push;

import com.example.short_link.common.federation.CustomEmoji;
import com.example.short_link.notification.domain.NotificationActor;

public record PushActor(
    Long id,
    Long remoteId,
    String username,
    String displayName,
    String avatarUrl,
    boolean speaking) {

  public static PushActor of(NotificationActor actor, boolean speaking) {
    if (actor == null || actor.username() == null) {
      return null;
    }
    if (actor.remoteId() != null) {
      return new PushActor(
          null, actor.remoteId(), actor.username(), plainName(actor), null, speaking);
    }
    String avatar = actor.avatarUrl();
    return new PushActor(
        actor.userId(),
        null,
        actor.username(),
        plainName(actor),
        avatar != null && avatar.startsWith("https://") ? avatar : null,
        speaking);
  }

  private static String plainName(NotificationActor actor) {
    String name = actor.displayName();
    if (name == null) {
      return null;
    }
    for (CustomEmoji emoji : actor.emojis()) {
      name = name.replace(":" + emoji.shortcode() + ":", " ");
    }
    name = name.replaceAll("[\\s\\u200B]+", " ").strip();
    return name.isEmpty() ? null : name;
  }
}
