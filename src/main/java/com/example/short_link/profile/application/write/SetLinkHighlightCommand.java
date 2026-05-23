package com.example.short_link.profile.application.write;

public record SetLinkHighlightCommand(Long userId, String shortCode, boolean highlighted) {

  public SetLinkHighlightCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (shortCode == null || shortCode.isBlank()) {
      throw new IllegalArgumentException("shortCode required");
    }
  }
}
