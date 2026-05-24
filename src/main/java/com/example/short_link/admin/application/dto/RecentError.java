package com.example.short_link.admin.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot of a single ERROR/WARN log entry captured by {@link RecentErrorsBuffer}. Surfaced via
 * the admin UI so operators can diagnose incidents without shell access. Fields beyond {@code
 * timestamp/level/logger/message} are populated best-effort from the SLF4J event + MDC; any field
 * may be {@code null} when the source log line didn't carry that context (e.g. scheduled tasks have
 * no requestUri/userId, plain log calls have no exception).
 */
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
