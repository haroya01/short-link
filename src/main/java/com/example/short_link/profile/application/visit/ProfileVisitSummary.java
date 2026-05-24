package com.example.short_link.profile.application.visit;

/**
 * Tiny stats card payload for the /profile/edit "mini summary" section. Just four rolling totals —
 * today / week / month / all time. Computed via four cheap COUNT queries on the indexed
 * (profile_user_id, visited_at) covering index.
 */
public record ProfileVisitSummary(long today, long week, long month, long allTime) {}
