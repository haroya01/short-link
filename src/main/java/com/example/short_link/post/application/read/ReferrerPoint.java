package com.example.short_link.post.application.read;

/** 윈도우 안의 사람 조회만 집계하며 레퍼러가 없는 direct 유입은 제외한다. */
public record ReferrerPoint(String host, long views) {}
