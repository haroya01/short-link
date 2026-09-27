package com.example.short_link.abuse.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ResolveAbuseReportRequest(
    @NotBlank String resolution,
    String action,
    Instant suspendUntil,
    @Size(max = 2000) String adminNote) {}
