package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.repository.PostReadMilestoneRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
class PostReadMilestoneRepositoryAdapter implements PostReadMilestoneRepository {

  @PersistenceContext private EntityManager em;

  @Override
  public boolean record(Long postId, int milestone, Instant reachedAt) {
    return em.createNativeQuery(
                "INSERT IGNORE INTO post_read_milestone (post_id, milestone, reached_at)"
                    + " VALUES (:postId, :milestone, :reachedAt)")
            .setParameter("postId", postId)
            .setParameter("milestone", milestone)
            .setParameter("reachedAt", reachedAt)
            .executeUpdate()
        == 1;
  }
}
