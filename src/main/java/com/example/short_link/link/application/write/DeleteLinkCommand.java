package com.example.short_link.link.application.write;

public record DeleteLinkCommand(Long userId, String shortCode) {

  public DeleteLinkCommand {
    if (userId == null) {
      throw new IllegalArgumentException("userId required");
    }
    if (shortCode == null || shortCode.isBlank()) {
      throw new IllegalArgumentException("shortCode required");
    }
  }
}
