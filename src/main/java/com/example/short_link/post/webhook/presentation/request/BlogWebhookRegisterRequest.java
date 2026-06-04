package com.example.short_link.post.webhook.presentation.request;

import com.example.short_link.common.event.BlogInteractionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Body for registering a blog webhook. {@code events} omitted ⇒ subscribe to all interactions. */
public record BlogWebhookRegisterRequest(
    @NotBlank @Size(max = 2048) String url,
    @Size(max = 100) String name,
    Set<BlogInteractionType> events) {}
