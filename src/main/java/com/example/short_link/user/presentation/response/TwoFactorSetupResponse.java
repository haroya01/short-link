package com.example.short_link.user.presentation.response;

import com.example.short_link.user.application.twofactor.TwoFactorService.SetupChallenge;

public record TwoFactorSetupResponse(String secret, String provisioningUri) {

  public static TwoFactorSetupResponse from(SetupChallenge c) {
    return new TwoFactorSetupResponse(c.secret(), c.provisioningUri());
  }
}
