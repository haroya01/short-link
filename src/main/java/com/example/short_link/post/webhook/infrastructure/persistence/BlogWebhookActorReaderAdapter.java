package com.example.short_link.post.webhook.infrastructure.persistence;

import com.example.short_link.post.webhook.domain.repository.BlogWebhookActorReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class BlogWebhookActorReaderAdapter implements BlogWebhookActorReader {

  @PersistenceContext private EntityManager em;

  @Override
  public Optional<String> usernameOf(Long userId) {
    if (userId == null) {
      return Optional.empty();
    }
    List<?> rows =
        em.createNativeQuery("SELECT username FROM users WHERE id = :id")
            .setParameter("id", userId)
            .getResultList();
    if (rows.isEmpty() || rows.get(0) == null) {
      return Optional.empty();
    }
    return Optional.of(rows.get(0).toString());
  }
}
