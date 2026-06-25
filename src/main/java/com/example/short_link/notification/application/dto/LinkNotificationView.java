package com.example.short_link.notification.application.dto;

import com.example.short_link.notification.domain.LinkNotificationType;
import java.time.Instant;

/** 링크 알림 인박스 한 줄의 표시 모델 — 링크 단위 필드(shortCode)를 직접 들고 있어 액터/페이로드 해석이 없다. */
public record LinkNotificationView(
    Long id,
    LinkNotificationType type,
    String shortCode,
    String subtitle,
    String body,
    boolean read,
    Instant createdAt) {}
