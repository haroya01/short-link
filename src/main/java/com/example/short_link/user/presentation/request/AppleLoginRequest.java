package com.example.short_link.user.presentation.request;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code nonce} is the raw value generated for this attempt; its SHA-256 must match the identity
 * token to bind it to this request.
 */
public record AppleLoginRequest(@NotBlank String identityToken, @NotBlank String nonce) {}
