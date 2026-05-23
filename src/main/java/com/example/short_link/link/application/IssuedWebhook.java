package com.example.short_link.link.application;

import java.time.Instant;

public record IssuedWebhook(
    Long id, String url, String secret, String name, Instant createdAt, WebhookFormat format) {}
