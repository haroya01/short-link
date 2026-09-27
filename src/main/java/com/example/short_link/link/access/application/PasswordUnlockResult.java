package com.example.short_link.link.access.application;

import com.example.short_link.link.redirect.application.RedirectOutcome;

public sealed interface PasswordUnlockResult {
  enum RejectionReason {
    CAPTCHA_FAILED,
    LOCKED_OUT,
    WRONG_PASSWORD
  }

  record Rejected(RejectionReason reason) implements PasswordUnlockResult {}

  record Completed(RedirectOutcome redirect) implements PasswordUnlockResult {}
}
