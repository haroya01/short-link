package com.example.short_link.link.access.application;

public interface TurnstileVerifier {

  boolean enabled();

  /** Accepts an unconfigured challenge; configured verification fails closed. */
  boolean verify(String token, String remoteIp);
}
