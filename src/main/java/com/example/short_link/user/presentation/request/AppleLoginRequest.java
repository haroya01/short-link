package com.example.short_link.user.presentation.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Native Sign in with Apple. {@code nonce} is the raw value the app generated for this attempt —
 * the identity token must carry its SHA-256, which is what binds the token to this request.
 */
public record AppleLoginRequest(@NotBlank String identityToken, @NotBlank String nonce) {}
