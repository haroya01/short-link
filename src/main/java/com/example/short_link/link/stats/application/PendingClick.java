package com.example.short_link.link.stats.application;

import java.time.Instant;

/**
 * One click waiting in the buffer. {@code occurredAt} is captured at enqueue time so a delayed
 * flush still stamps {@code clicked_at} with the moment the person actually clicked.
 */
public record PendingClick(ClickContext ctx, String forcedBotName, Instant occurredAt) {}
