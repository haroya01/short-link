package com.example.short_link.post.collection.domain;

/** 컬렉션에 연결되는 블록의 종류 — 글·하이라이트·노트. ref_id 가 가리키는 테이블을 정한다. */
public enum ConnectionBlockType {
  POST,
  HIGHLIGHT,
  NOTE
}
