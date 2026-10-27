package com.example.short_link.common.user;

import com.example.short_link.common.event.Actor;

public record SilenceCheck(Long recipientId, Actor actor, Long conversationId) {

  public static SilenceCheck of(Long recipientId, Actor actor) {
    return new SilenceCheck(recipientId, actor, null);
  }

  public SilenceCheck inConversation(Long conversationId) {
    return new SilenceCheck(recipientId, actor, conversationId);
  }
}
