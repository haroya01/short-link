package com.example.short_link.post.collection.domain;

public enum CollectionKind {
  COLLECTION,
  PATH;

  public static CollectionKind of(boolean ordered) {
    return ordered ? PATH : COLLECTION;
  }
}
