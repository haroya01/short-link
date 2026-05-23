package com.example.short_link.billing.application.write;

public record StartCheckoutCommand(Long userId) {

  public StartCheckoutCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
  }
}
