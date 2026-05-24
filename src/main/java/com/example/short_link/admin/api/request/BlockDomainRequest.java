package com.example.short_link.admin.api.request;

import jakarta.validation.constraints.NotBlank;

public record BlockDomainRequest(@NotBlank String domain, String reason) {}
