package com.example.short_link.user.presentation.response;

import com.example.short_link.user.application.dto.IssuedTokens;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Either a full token pair, or only {@code challenge} when the account has 2FA enabled — the app
 * then finishes through the shared {@code /2fa/verify} with that challenge token.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppleLoginResponse(String accessToken, String refreshToken, String challenge) {

  public static AppleLoginResponse tokens(IssuedTokens tokens) {
    return new AppleLoginResponse(tokens.accessToken(), tokens.refreshToken(), null);
  }

  public static AppleLoginResponse twoFactor(String challenge) {
    return new AppleLoginResponse(null, null, challenge);
  }
}
