package com.example.short_link.post.collection.application.write;

import com.example.short_link.post.collection.domain.CollectionVisibility;

public record EditCollectionCommand(
    Long userId,
    Long collectionId,
    String title,
    String description,
    CollectionVisibility visibility) {

  public EditCollectionCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (collectionId == null) throw new IllegalArgumentException("collectionId required");
    if (visibility == null) visibility = CollectionVisibility.PRIVATE;
  }
}
