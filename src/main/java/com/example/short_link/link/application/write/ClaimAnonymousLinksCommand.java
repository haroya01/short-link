package com.example.short_link.link.application.write;

import java.util.Collection;
import java.util.List;

public record ClaimAnonymousLinksCommand(Long userId, List<String> claimTokens) {

  public ClaimAnonymousLinksCommand {
    if (userId == null) {
      throw new IllegalArgumentException("userId required");
    }
    claimTokens = claimTokens == null ? List.of() : List.copyOf(claimTokens);
  }

  public static ClaimAnonymousLinksCommand of(Long userId, Collection<String> tokens) {
    return new ClaimAnonymousLinksCommand(userId, tokens == null ? List.of() : List.copyOf(tokens));
  }
}
