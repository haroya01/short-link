package com.example.short_link.common.notification;

public record LinkNotice(
    Long ownerUserId, LinkNoticeKind kind, String shortCode, String label, LinkNoticeBody body) {}
