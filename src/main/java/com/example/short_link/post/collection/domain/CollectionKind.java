package com.example.short_link.post.collection.domain;

/**
 * 컬렉션의 읽기 형태. COLLECTION = 주제로 묶은 더미(순서는 약한 신호), PATH = 순서가 곧 논증 흐름인 reading path(A 척추 — 여러 글의 문장을
 * 가로질러 하나의 흐름으로 엮는다). 클라이언트가 PATH 면 리스트가 아니라 가이드 워크로 읽는다.
 */
public enum CollectionKind {
  COLLECTION,
  PATH
}
