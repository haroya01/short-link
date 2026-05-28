package com.example.short_link.abuse.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitAbuseReportRequest(
    @NotBlank String subjectType, @NotNull Long subjectId, @Size(max = 2000) String reason) {}
