package com.example.short_link.user.presentation.response;

import com.example.short_link.user.application.dto.IssuedTokens;

public record TokenResponse(String accessToken) {

  public static TokenResponse from(IssuedTokens tokens) {
    return new TokenResponse(tokens.accessToken());
  }
}
