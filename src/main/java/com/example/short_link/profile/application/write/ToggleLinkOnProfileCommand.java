package com.example.short_link.profile.application.write;

public record ToggleLinkOnProfileCommand(Long userId, String shortCode, boolean show) {

  public ToggleLinkOnProfileCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (shortCode == null || shortCode.isBlank()) {
      throw new IllegalArgumentException("shortCode required");
    }
  }
}
