package com.example.short_link.post.collection.application.read;

import java.util.List;

/** 공개 컬렉션에 속하지 않은 글은 {@code collections}가 빈 목록이다. */
public record PostCollectionsView(Long postId, List<CollectionSummaryView> collections) {}
