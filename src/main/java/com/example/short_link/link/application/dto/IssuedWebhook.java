package com.example.short_link.link.application.dto;

import com.example.short_link.link.application.helper.WebhookFormat;
import java.time.Instant;

public record IssuedWebhook(
    Long id, String url, String secret, String name, Instant createdAt, WebhookFormat format) {}
