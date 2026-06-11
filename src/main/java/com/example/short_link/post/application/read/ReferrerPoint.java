package com.example.short_link.post.application.read;

/**
 * 개요 대시보드 "유입 경로"의 한 행 — 윈도우 안에서 이 호스트가 보낸 사람 조회 수. direct(레퍼러 없음)는 집계에서 빠진다(글 단위 독자 분석과 같은 의미론).
 */
public record ReferrerPoint(String host, long views) {}
