package com.example.short_link.user.api.request;

import jakarta.validation.constraints.NotBlank;

public record TwoFactorVerifyRequest(
    @NotBlank String challenge, @NotBlank String code, boolean recovery) {}
