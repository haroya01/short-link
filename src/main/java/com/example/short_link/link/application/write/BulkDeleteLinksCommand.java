package com.example.short_link.link.application.write;

import java.util.Collection;
import java.util.List;

public record BulkDeleteLinksCommand(Long userId, List<String> shortCodes) {

  public BulkDeleteLinksCommand {
    if (userId == null) {
      throw new IllegalArgumentException("userId required");
    }
    shortCodes = shortCodes == null ? List.of() : List.copyOf(shortCodes);
  }

  public static BulkDeleteLinksCommand of(Long userId, Collection<String> codes) {
    return new BulkDeleteLinksCommand(userId, codes == null ? List.of() : List.copyOf(codes));
  }
}
