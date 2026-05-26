package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.ShortCode;

public record DeleteLinkCommand(Long userId, ShortCode shortCode) {

  public DeleteLinkCommand {
    if (userId == null) {
      throw new IllegalArgumentException("userId required");
    }
    if (shortCode == null) {
      throw new IllegalArgumentException("shortCode required");
    }
  }
}
