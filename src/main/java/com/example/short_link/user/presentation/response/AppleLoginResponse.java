package com.example.short_link.user.presentation.response;

import com.example.short_link.user.application.dto.IssuedTokens;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Contains either a token pair or only a 2FA challenge, which must be completed through
 * /2fa/verify.
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
