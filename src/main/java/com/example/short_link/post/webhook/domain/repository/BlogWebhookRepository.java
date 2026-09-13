package com.example.short_link.post.webhook.domain.repository;

import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import java.util.List;
import java.util.Optional;

public interface BlogWebhookRepository {

  BlogWebhookEntity save(BlogWebhookEntity hook);

  Optional<BlogWebhookEntity> findByIdAndUserId(Long id, Long userId);

  /** Newest first. */
  List<BlogWebhookEntity> findAllByUserId(Long userId);

  /** Filters enabled hooks by owner; the dispatcher applies interaction-type filtering. */
  List<BlogWebhookEntity> findAllByUserIdAndEnabledTrue(Long userId);

  long countByUserId(Long userId);

  void delete(BlogWebhookEntity hook);
}
