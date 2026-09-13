package com.example.short_link.user.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Success returns accessToken and sets an HTTP-only refresh cookie. For 2FA users only challenge is
 * set; complete it through /api/v1/auth/2fa/verify.
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
