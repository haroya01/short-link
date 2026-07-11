package com.example.short_link.post.collection.domain;

/**
 * 컬렉션 안에서 한 연결의 순위(window-function 투영) — {@code position} 은 그 컬렉션의 정렬된 연결 목록에서 이 (blockType, refId)
 * 의 1-based 자리다. 저장된 raw position 값이 삭제·재배치로 듬성해질 수 있어 그 값을 그대로 쓰지 않고 정렬 순서로 다시 센다. 여러 컬렉션의 순위를 한
 * 쿼리로 받아 글마다 세지 않는다(N+1 방지).
 */
public record CollectionConnectionRank(Long collectionId, Long refId, int position) {}
