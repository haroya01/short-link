package com.example.short_link.notification.domain;

/**
 * Owner-facing push notifications about a person's own short links. Kept separate from {@link
 * NotificationType} (the blog interaction bell) — these are push-only, not surfaced in the blog
 * bell, and each is opt-out via {@code notification_preference}. A privacy-first quiet tool: every
 * type defaults on at the OS-permission gate but stays restrained (no per-click pings).
 *
 * <ul>
 *   <li>{@code FIRST_CLICK} — a link's very first human click.
 *   <li>{@code MILESTONE} — round human-click thresholds (100, 1k, 10k, …).
 *   <li>{@code VELOCITY_SPIKE} — clicks running well above the link's recent baseline.
 *   <li>{@code EXPIRY_IMMINENT} — a link with an expiry date entering its final week.
 *   <li>{@code DIGEST} — one calm daily summary of yesterday's clicks + top link.
 *   <li>{@code WARNING} — an operator policy notice. Not opt-out-able: excluded from the settings
 *       map and the dispatcher skips the preference gate, because terms §7 notices must reach the
 *       account regardless of push preferences.
 * </ul>
 */
public enum LinkNotificationType {
  FIRST_CLICK,
  MILESTONE,
  VELOCITY_SPIKE,
  EXPIRY_IMMINENT,
  DIGEST,
  WARNING
}
