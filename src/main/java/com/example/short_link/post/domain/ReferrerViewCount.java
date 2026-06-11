package com.example.short_link.post.domain;

/**
 * 한 유입 호스트(레퍼러)의 조회 합계 — post_view_event 의 GROUP BY referrer_host 출력. 사람 조회만 집계하고, referrer 가 없는
 * direct 유입은 행이 없다(글 단위 독자 분석의 topReferrerHosts 와 같은 의미론).
 */
public record ReferrerViewCount(String host, long views) {}
