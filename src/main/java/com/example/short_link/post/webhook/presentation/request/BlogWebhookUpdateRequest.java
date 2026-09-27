package com.example.short_link.post.webhook.presentation.request;

import com.example.short_link.common.event.BlogInteractionType;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record BlogWebhookUpdateRequest(
    @Size(max = 100) String name, Set<BlogInteractionType> events, Boolean enabled) {}
