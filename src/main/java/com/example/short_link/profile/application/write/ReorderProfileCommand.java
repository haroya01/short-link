package com.example.short_link.profile.application.write;

import java.util.List;

public record ReorderProfileCommand(Long userId, List<ReorderItem> items) {

  public ReorderProfileCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (items == null) items = List.of();
  }
}
