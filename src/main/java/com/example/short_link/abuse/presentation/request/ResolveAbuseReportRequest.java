package com.example.short_link.abuse.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** {@code action} 생략 시 NONE으로 처리한다. {@code suspendUntil}은 SUSPEND_USER에만 사용한다. */
public record ResolveAbuseReportRequest(
    @NotBlank String resolution,
    String action,
    Instant suspendUntil,
    @Size(max = 2000) String adminNote) {}
