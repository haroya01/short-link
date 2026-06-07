package com.example.short_link.common.event;

import java.time.Instant;

/**
 * Published when a post goes public for the first time (not on edit, re-publish, or scheduled-park
 * — only the initial publish). Consumed by the notification module to fan out a NEW_POST notice to
 * each of the author's followers. Notification-only and a 1-to-many fan-out concern, so it's kept
 * separate from {@link BlogInteractionEvent} (which is a single-recipient webhook signal).
 *
 * <p>No author username is carried: the author IS the notification actor, so each NEW_POST row's
 * author handle is resolved at read time alongside the actor — the same path LIKE/COMMENT use.
 *
 * @param authorUserId the publishing author (the notification actor); never notified themselves
 * @param postId the published post
 * @param postSlug post slug snapshot
 * @param postTitle post title snapshot
 * @param occurredAt when the post was published
 */
public record PostPublishedEvent(
    Long authorUserId, Long postId, String postSlug, String postTitle, Instant occurredAt) {}
