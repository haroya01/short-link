package com.example.short_link.profile.application.write;

import com.example.short_link.profile.domain.ProfileBlockType;

public record CreateBlockCommand(Long userId, ProfileBlockType type, String content) {

  public CreateBlockCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (type == null) throw new IllegalArgumentException("type required");
  }
}
