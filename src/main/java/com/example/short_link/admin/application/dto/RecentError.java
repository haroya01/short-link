package com.example.short_link.admin.application.dto;

import java.time.Instant;
import java.util.List;

/** 요청·사용자·예외 정보는 로그에 해당 문맥이 없으면 null이다. */
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
