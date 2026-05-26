package com.example.short_link.profile.application.write;

import com.example.short_link.link.domain.ShortCode;

public record ToggleLinkOnProfileCommand(Long userId, ShortCode shortCode, boolean show) {

  public ToggleLinkOnProfileCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (shortCode == null) {
      throw new IllegalArgumentException("shortCode required");
    }
  }
}
