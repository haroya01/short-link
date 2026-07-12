package com.example.short_link.abuse.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * 신고 처리 요청. {@code resolution} 은 상태 전이(REVIEWING/RESOLVED/REJECTED), {@code action} 은 함께 집행할
 * 조치(생략/null 이면 NONE = 집행 없음). {@code suspendUntil} 은 action=SUSPEND_USER 일 때만 쓰인다.
 */
public record ResolveAbuseReportRequest(
    @NotBlank String resolution,
    String action,
    Instant suspendUntil,
    @Size(max = 2000) String adminNote) {}
