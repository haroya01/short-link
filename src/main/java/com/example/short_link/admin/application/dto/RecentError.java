package com.example.short_link.admin.application.dto;

import java.time.Instant;
import java.util.List;

public record RecentError(
    Instant timestamp,
    String level,
    String logger,
    String thread,
    String message,
    String exceptionClass,
    String exceptionMessage,
    List<String> causeChain,
    String stackTrace,
    String requestId,
    String requestUri,
    String requestMethod,
    String userId,
    String clientIp,
    String taskName) {}
