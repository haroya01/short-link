package com.example.short_link.user.presentation.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppleWebLoginResponse(String accessToken, String challenge) {

  public static AppleWebLoginResponse tokens(String accessToken) {
    return new AppleWebLoginResponse(accessToken, null);
  }

  public static AppleWebLoginResponse twoFactor(String challenge) {
    return new AppleWebLoginResponse(null, challenge);
  }
}
