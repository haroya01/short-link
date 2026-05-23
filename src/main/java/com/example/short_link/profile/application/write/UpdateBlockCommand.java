package com.example.short_link.profile.application.write;

public record UpdateBlockCommand(Long userId, Long blockId, String content) {

  public UpdateBlockCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (blockId == null) throw new IllegalArgumentException("blockId required");
  }
}
