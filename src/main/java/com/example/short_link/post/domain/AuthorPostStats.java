package com.example.short_link.post.domain;

/** An author's published-post tallies — ranks the discovery rail's 추천 작가 list. */
public record AuthorPostStats(Long authorId, long postCount, long totalViews) {}
