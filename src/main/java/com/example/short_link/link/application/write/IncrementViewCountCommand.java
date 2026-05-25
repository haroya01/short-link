package com.example.short_link.link.application.write;

public record IncrementViewCountCommand(Long linkId) {

  public IncrementViewCountCommand {
    if (linkId == null) {
      throw new IllegalArgumentException("linkId required");
    }
  }
}
