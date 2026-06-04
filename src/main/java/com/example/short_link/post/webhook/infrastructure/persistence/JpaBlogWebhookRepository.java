package com.example.short_link.post.webhook.infrastructure.persistence;

import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaBlogWebhookRepository extends JpaRepository<BlogWebhookEntity, Long> {

  Optional<BlogWebhookEntity> findByIdAndUserId(Long id, Long userId);

  List<BlogWebhookEntity> findAllByUserIdOrderByIdDesc(Long userId);

  List<BlogWebhookEntity> findAllByUserIdAndEnabledTrue(Long userId);

  long countByUserId(Long userId);
}
