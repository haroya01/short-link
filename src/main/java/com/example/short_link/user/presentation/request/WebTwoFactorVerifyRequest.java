package com.example.short_link.user.presentation.request;

import jakarta.validation.constraints.NotBlank;

// challenge is optional because a web OAuth login carries it in an HttpOnly cookie instead.
public record WebTwoFactorVerifyRequest(
    String challenge, @NotBlank String code, boolean recovery) {}
