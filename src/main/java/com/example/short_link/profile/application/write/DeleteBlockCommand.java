package com.example.short_link.profile.application.write;

public record DeleteBlockCommand(Long userId, Long blockId) {

  public DeleteBlockCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (blockId == null) throw new IllegalArgumentException("blockId required");
  }
}
