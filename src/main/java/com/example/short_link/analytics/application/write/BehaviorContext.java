package com.example.short_link.analytics.application.write;

/** 비콘 요청에서 온 방문자 신호 — 봇 판정·기기 분류·방문자 해시에 쓰인다. IP 는 판정에만 쓰고 저장하지 않는다. */
public record BehaviorContext(String userAgent, String clientIp, boolean gpc) {}
