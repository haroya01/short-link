package com.example.short_link.post.webhook.application.dto;

import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.post.webhook.domain.BlogWebhookFormat;
import java.time.Instant;
import java.util.Set;

/** Returned once at registration — the only time the plaintext {@code secret} is exposed. */
public record IssuedBlogWebhook(
    Long id,
    String url,
    String secret,
    String name,
    BlogWebhookFormat format,
    Set<BlogInteractionType> events,
    Instant createdAt) {}
