package com.example.short_link.user.presentation.response;

import com.example.short_link.user.application.dto.IssuedTokens;

/** Unlike {@link TokenResponse}, carries the refresh token too — the app has no cookie jar. */
public record MobileTokenResponse(String accessToken, String refreshToken) {

  public static MobileTokenResponse from(IssuedTokens tokens) {
    return new MobileTokenResponse(tokens.accessToken(), tokens.refreshToken());
  }
}
