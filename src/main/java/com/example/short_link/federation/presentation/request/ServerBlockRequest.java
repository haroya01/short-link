package com.example.short_link.federation.presentation.request;

import com.example.short_link.federation.domain.ServerBlockSeverity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ServerBlockRequest(
    @NotNull ServerBlockSeverity severity, @Size(max = 500) String reason) {}
