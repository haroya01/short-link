package com.example.short_link.post.collection.domain;

public enum CollectionVisibility {
  PRIVATE,
  UNLISTED,
  PUBLIC;

  public boolean isVisibleToOthers() {
    return this != PRIVATE;
  }
}
