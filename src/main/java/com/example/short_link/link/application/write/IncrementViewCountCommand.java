package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.LinkId;

public record IncrementViewCountCommand(LinkId linkId) {

  public IncrementViewCountCommand {
    if (linkId == null) {
      throw new IllegalArgumentException("linkId required");
    }
  }
}
