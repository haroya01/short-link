package com.example.short_link.user.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 브라우저 PushSubscription.toJSON()의 endpoint와 중첩된 keys를 평탄화한 요청이다. */
public record SubscribeWebPushRequest(
    @NotBlank @Size(max = 512) String endpoint,
    @NotBlank @Size(max = 255) String p256dh,
    @NotBlank @Size(max = 255) String auth) {}
