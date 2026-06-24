package com.example.short_link.user.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Web "Sign in with Apple" result. On success the refresh token rides an HTTP-only cookie (never
 * the body), so only the access token comes back here — the mirror of {@link TokenResponse}. With
 * 2FA enabled only {@code challenge} is set, and the browser finishes through {@code
 * /api/v1/auth/2fa/verify} (the same endpoint the Google flow uses).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppleWebLoginResponse(String accessToken, String challenge) {

  public static AppleWebLoginResponse tokens(String accessToken) {
    return new AppleWebLoginResponse(accessToken, null);
  }

  public static AppleWebLoginResponse twoFactor(String challenge) {
    return new AppleWebLoginResponse(null, challenge);
  }
}
