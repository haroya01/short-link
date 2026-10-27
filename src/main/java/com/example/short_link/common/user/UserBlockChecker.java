package com.example.short_link.common.user;

// user 슬라이스가 구현하며, 댓글 생성 경로와의 순환 의존을 막는다.
public interface UserBlockChecker {

  boolean isBlocked(BlockEdge edge);

  // Both directions in one read; nothing for an anonymous viewer or the viewer themself.
  BlockRelation between(Long viewerId, Long otherId);

  // The recipient blocked the actor, or muted them with their notices; an actor elsewhere also when
  // the recipient blocked their server. With a conversation, also when the recipient muted it
  // (Mastodon's mute conversation).
  boolean silences(SilenceCheck check);
}
