package com.example.short_link.common.notification;

/**
 * The blog-bell notification kinds a producer slice (post comments / highlight replies) can ask
 * about before deciding whether a recipient's @-mention collapses into the notice they already get.
 * Only the kinds a producer actually needs to gate the mention dedup on live here — MENTION itself
 * is never gated this way (a muted mention is filtered at record time by the recipient's own
 * MENTION opt-out). Lives in the shared kernel so the post slice references it without a
 * post→notification dependency; the notification slice maps it to its own {@code NotificationType}.
 */
public enum BlogNotificationKind {
  COMMENT,
  REPLY
}
