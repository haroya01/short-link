package com.example.short_link.post.webhook.infrastructure.persistence;

import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaBlogWebhookRepository
    extends JpaRepository<BlogWebhookEntity, Long>, BlogWebhookRepository {}
