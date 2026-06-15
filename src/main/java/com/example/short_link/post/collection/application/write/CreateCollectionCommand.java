package com.example.short_link.post.collection.application.write;

import com.example.short_link.post.collection.domain.CollectionVisibility;

public record CreateCollectionCommand(
    Long userId, String title, String description, CollectionVisibility visibility) {

  public CreateCollectionCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (visibility == null) visibility = CollectionVisibility.PRIVATE;
  }
}
