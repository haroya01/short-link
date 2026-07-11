package com.example.short_link.common.notification;

/**
 * Neutral read port letting a producer slice (post comments / highlight replies) check whether a
 * recipient has muted a blog-bell {@link BlogNotificationKind}, without a post→notification
 * dependency. Mirrors how {@code common.post.PublishedPostCountReader} and {@code
 * common.cache.ProfileCacheInvalidator} keep the slice graph acyclic (ArchUnit-enforced); the
 * notification slice provides the implementation.
 *
 * <p>Producers use it only to gate @-mention dedup: a recipient is folded into the COMMENT/REPLY
 * they already receive <em>only when they actually receive it</em>. When they've muted that kind,
 * they are left un-folded so an explicit @-mention of them still fires — the mention is then
 * subject to the recipient's own MENTION opt-out at record time.
 */
public interface BlogNotificationMuteReader {

  /** Whether {@code userId} has opted out of the given blog-bell kind (absent preference = on). */
  boolean isMuted(Long userId, BlogNotificationKind kind);
}
