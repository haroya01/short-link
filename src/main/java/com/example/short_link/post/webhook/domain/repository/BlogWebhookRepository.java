package com.example.short_link.post.webhook.domain.repository;

import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import java.util.List;
import java.util.Optional;

/** Persistence port for an author's blog notification webhooks. */
public interface BlogWebhookRepository {

  BlogWebhookEntity save(BlogWebhookEntity hook);

  Optional<BlogWebhookEntity> findByIdAndUserId(Long id, Long userId);

  /** Every hook the author owns — newest first — for the settings list. */
  List<BlogWebhookEntity> findAllByUserId(Long userId);

  /**
   * Enabled hooks for one recipient — the dispatcher filters these by interaction type in memory.
   */
  List<BlogWebhookEntity> findAllByUserIdAndEnabledTrue(Long userId);

  long countByUserId(Long userId);

  void delete(BlogWebhookEntity hook);
}
