package com.example.short_link.link.access.application;

/** Verifies the optional bot challenge before the password unlock flow proceeds. */
public interface TurnstileVerifier {

  boolean enabled();

  /** Accepts an unconfigured challenge; configured verification fails closed. */
  boolean verify(String token, String remoteIp);
}
