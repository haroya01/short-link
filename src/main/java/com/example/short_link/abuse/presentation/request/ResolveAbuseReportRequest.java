package com.example.short_link.abuse.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveAbuseReportRequest(
    @NotBlank String resolution, @Size(max = 2000) String adminNote) {}
