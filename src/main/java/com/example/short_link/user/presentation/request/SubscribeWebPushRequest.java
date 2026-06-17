package com.example.short_link.user.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 브라우저 PushSubscription.toJSON() 을 평탄화한 모양 — {@code endpoint} + 키 두 개(p256dh·auth). 프론트가 구독을 받을 때마다
 * 보낸다(멱등 upsert).
 */
public record SubscribeWebPushRequest(
    @NotBlank @Size(max = 512) String endpoint,
    @NotBlank @Size(max = 255) String p256dh,
    @NotBlank @Size(max = 255) String auth) {}
