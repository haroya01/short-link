package com.example.short_link.user.application.write;

import java.util.Optional;

// Short-lived, single-use codes carry browser OAuth results through the app's custom-scheme
// redirect without exposing a token pair.
public interface MobileExchangeCodeStore {

  String create(Long userId);

  Optional<Long> consume(String code);
}
