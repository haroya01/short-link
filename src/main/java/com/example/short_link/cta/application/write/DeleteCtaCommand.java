package com.example.short_link.cta.application.write;

public record DeleteCtaCommand(Long userId, Long ctaId) {

  public DeleteCtaCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (ctaId == null) throw new IllegalArgumentException("ctaId required");
  }
}
