package com.example.short_link.post.webhook.infrastructure.persistence;

import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class BlogWebhookRepositoryAdapter implements BlogWebhookRepository {

  private final JpaBlogWebhookRepository jpa;

  @Override
  public BlogWebhookEntity save(BlogWebhookEntity hook) {
    return jpa.save(hook);
  }

  @Override
  public Optional<BlogWebhookEntity> findByIdAndUserId(Long id, Long userId) {
    return jpa.findByIdAndUserId(id, userId);
  }

  @Override
  public List<BlogWebhookEntity> findAllByUserId(Long userId) {
    return jpa.findAllByUserIdOrderByIdDesc(userId);
  }

  @Override
  public List<BlogWebhookEntity> findAllByUserIdAndEnabledTrue(Long userId) {
    return jpa.findAllByUserIdAndEnabledTrue(userId);
  }

  @Override
  public long countByUserId(Long userId) {
    return jpa.countByUserId(userId);
  }

  @Override
  public void delete(BlogWebhookEntity hook) {
    jpa.delete(hook);
  }
}
