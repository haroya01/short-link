package com.example.short_link.user.presentation.response;

import com.example.short_link.user.application.dto.IssuedTokens;

public record MobileTokenResponse(String accessToken, String refreshToken) {

  public static MobileTokenResponse from(IssuedTokens tokens) {
    return new MobileTokenResponse(tokens.accessToken(), tokens.refreshToken());
  }
}
