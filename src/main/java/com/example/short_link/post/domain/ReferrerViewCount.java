package com.example.short_link.post.domain;

/** 사람 조회만 집계한다. 레퍼러가 없는 direct 유입은 제외한다. */
public record ReferrerViewCount(String host, long views) {}
