package com.example.short_link.post.application.write;

import com.example.short_link.common.notification.BlogNotificationKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CommentNotificationPlan {

  interface Lookups {
    boolean reads(Long userId);

    boolean muted(Long userId, BlogNotificationKind kind);

    Long activeUserId(String handle);
  }

  private final Long actorId;
  private final Lookups lookups;
  private final boolean ownerHearsComment;
  private final boolean authorHearsReply;
  private final Set<Long> mentionSuppressed;

  private CommentNotificationPlan(
      Long actorId,
      Lookups lookups,
      boolean ownerHearsComment,
      boolean authorHearsReply,
      Set<Long> mentionSuppressed) {
    this.actorId = actorId;
    this.lookups = lookups;
    this.ownerHearsComment = ownerHearsComment;
    this.authorHearsReply = authorHearsReply;
    this.mentionSuppressed = mentionSuppressed;
  }

  static CommentNotificationPlan forComment(
      Long ownerId, Long actorId, Long parentAuthorId, Lookups lookups) {
    Set<Long> suppressed = new HashSet<>();
    // 소유자에게 직접 답한 경우 REPLY가 우선한다. REPLY를 꺼도 COMMENT로 대체하지 않는다.
    boolean replyToOwner = ownerId.equals(parentAuthorId);
    boolean ownerHearsComment = !ownerId.equals(actorId) && !replyToOwner;
    if (ownerHearsComment) {
      suppressUnlessMuted(suppressed, ownerId, BlogNotificationKind.COMMENT, lookups);
    }
    boolean parentHearsReply =
        parentAuthorId != null && !parentAuthorId.equals(actorId) && lookups.reads(parentAuthorId);
    if (parentHearsReply) {
      suppressUnlessMuted(suppressed, parentAuthorId, BlogNotificationKind.REPLY, lookups);
    }
    return new CommentNotificationPlan(
        actorId, lookups, ownerHearsComment, parentHearsReply, suppressed);
  }

  static CommentNotificationPlan forHighlightReply(
      Long highlightAuthorId, Long actorId, Lookups lookups) {
    Set<Long> suppressed = new HashSet<>();
    boolean authorHearsReply =
        !highlightAuthorId.equals(actorId) && lookups.reads(highlightAuthorId);
    if (authorHearsReply) {
      suppressUnlessMuted(suppressed, highlightAuthorId, BlogNotificationKind.REPLY, lookups);
    }
    return new CommentNotificationPlan(actorId, lookups, false, authorHearsReply, suppressed);
  }

  boolean ownerHearsComment() {
    return ownerHearsComment;
  }

  boolean authorHearsReply() {
    return authorHearsReply;
  }

  List<Long> mentioned(List<String> handles) {
    Set<Long> taken = new HashSet<>(mentionSuppressed);
    List<Long> mentioned = new ArrayList<>();
    for (String handle : handles) {
      Long recipient = lookups.activeUserId(handle);
      if (recipient != null
          && !recipient.equals(actorId)
          && lookups.reads(recipient)
          && taken.add(recipient)) {
        mentioned.add(recipient);
      }
    }
    return mentioned;
  }

  private static void suppressUnlessMuted(
      Set<Long> recipients, Long recipient, BlogNotificationKind kind, Lookups lookups) {
    // 이벤트 발행은 전달 완료가 아니다. 수신 거부한 COMMENT/REPLY는 명시적 멘션을 억제하지 않는다.
    if (!lookups.muted(recipient, kind)) recipients.add(recipient);
  }
}
