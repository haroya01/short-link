package com.example.short_link.link.stats.application;

import java.time.Instant;

public record PendingClick(ClickContext ctx, String forcedBotName, Instant occurredAt) {}
