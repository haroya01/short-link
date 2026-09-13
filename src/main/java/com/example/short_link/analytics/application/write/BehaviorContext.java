package com.example.short_link.analytics.application.write;

/** IP는 분류와 해시 생성에만 쓰며 원문을 저장하지 않는다. */
public record BehaviorContext(String userAgent, String clientIp, boolean gpc) {}
