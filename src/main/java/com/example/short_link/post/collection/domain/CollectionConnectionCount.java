package com.example.short_link.post.collection.domain;

/** 컬렉션별 담긴 연결 수(group-by 투영) — 여러 컬렉션의 count 를 한 쿼리로 받아 카드마다 세지 않게 한다(N+1 방지). */
public record CollectionConnectionCount(Long collectionId, long count) {}
