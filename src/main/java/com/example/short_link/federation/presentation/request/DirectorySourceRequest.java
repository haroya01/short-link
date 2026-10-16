package com.example.short_link.federation.presentation.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DirectorySourceRequest(@NotNull Boolean enabled, @Min(80) @Max(5000) Integer cap) {}
