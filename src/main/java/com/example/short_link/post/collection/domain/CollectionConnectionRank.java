package com.example.short_link.post.collection.domain;

/** {@code position}은 (position, id) 정렬의 1-based 순위다. 삭제·재배치로 저장 위치가 듬성해져도 연속된 순위를 반환한다. */
public record CollectionConnectionRank(Long collectionId, Long refId, int position) {}
