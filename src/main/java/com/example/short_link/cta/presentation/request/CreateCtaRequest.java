package com.example.short_link.cta.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCtaRequest(
    @NotBlank @Size(max = 100) String label,
    @NotBlank @Size(max = 2048) String url,
    String style,
    String purpose) {}
