package com.example.short_link.user.application.push;

public record WebPushTarget(String endpoint, String p256dh, String auth) {}
