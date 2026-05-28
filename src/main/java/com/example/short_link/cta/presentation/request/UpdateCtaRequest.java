package com.example.short_link.cta.presentation.request;

import jakarta.validation.constraints.Size;

public record UpdateCtaRequest(
    @Size(min = 1, max = 100) String label,
    @Size(min = 1, max = 2048) String url,
    String style,
    String purpose) {}
