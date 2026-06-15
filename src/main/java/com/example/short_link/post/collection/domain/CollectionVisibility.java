package com.example.short_link.post.collection.domain;

/** 컬렉션 공개 범위. PUBLIC·UNLISTED 는 누구나 보고, PRIVATE 는 주인만 본다. */
public enum CollectionVisibility {
  PRIVATE,
  UNLISTED,
  PUBLIC;

  /** 비주인에게도 보이는가 — PRIVATE 만 막는다(UNLISTED 는 링크를 아는 사람에게 열린다). */
  public boolean isVisibleToOthers() {
    return this != PRIVATE;
  }
}
