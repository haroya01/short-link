package com.example.short_link.post.collection.domain;

/** 컬렉션 공개 범위. PUBLIC·UNLISTED 는 누구나 보고, PRIVATE 는 주인만 본다. */
public enum CollectionVisibility {
  PRIVATE,
  UNLISTED,
  PUBLIC;

  public boolean isVisibleToOthers() {
    return this != PRIVATE;
  }
}
