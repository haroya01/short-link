package com.example.short_link.admin.application;

import java.time.Instant;

public record RecentError(
    Instant timestamp,
    String level,
    String logger,
    String message,
    String requestId,
    String exception) {}
