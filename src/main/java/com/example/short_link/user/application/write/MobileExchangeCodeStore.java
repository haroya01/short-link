package com.example.short_link.user.application.write;

import java.util.Optional;

/**
 * Short-lived, single-use codes carry browser OAuth results through the app's custom-scheme
 * redirect without exposing a token pair.
 */
public interface MobileExchangeCodeStore {

  String create(Long userId);

  /** Redeem {@code code}, deleting it atomically. Empty if unknown, expired, or already used. */
  Optional<Long> consume(String code);
}
