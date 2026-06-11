package com.example.short_link.user.application.write;

import java.util.Optional;

/**
 * One-time codes that bridge the browser-based OAuth dance into the native app. The success handler
 * mints a code and hands it to the app via the custom-scheme redirect; the app trades it for a
 * token pair exactly once — the code is short-lived and consumed on first read, so a leaked
 * redirect URL is worthless moments later.
 */
public interface MobileExchangeCodeStore {

  /** Mint a single-use code bound to {@code userId}. */
  String create(Long userId);

  /** Redeem {@code code}, deleting it atomically. Empty if unknown, expired, or already used. */
  Optional<Long> consume(String code);
}
