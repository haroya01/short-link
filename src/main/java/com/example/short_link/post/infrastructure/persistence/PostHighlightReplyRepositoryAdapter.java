package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.common.user.HeardSql;
import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostHighlightReplyRepositoryAdapter implements PostHighlightReplyRepository {

  private final JpaPostHighlightReplyRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public PostHighlightReplyEntity save(PostHighlightReplyEntity reply) {
    return jpa.save(reply);
  }

  @Override
  public Optional<PostHighlightReplyEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public void delete(PostHighlightReplyEntity reply) {
    jpa.delete(reply);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PostHighlightReplyEntity> findHeardByHighlightId(Long highlightId, Long viewerId) {
    return em.createNativeQuery(
            "SELECT r.* FROM highlight_reply r WHERE r.highlight_id = :highlightId"
                + " AND r.deleted_at IS NULL"
                + HeardSql.heard("r.user_id")
                + " AND EXISTS (SELECT 1 FROM post_highlight h WHERE h.id = r.highlight_id"
                + HeardSql.heard("h.user_id")
                + ")"
                + " ORDER BY r.created_at ASC",
            PostHighlightReplyEntity.class)
        .setParameter("highlightId", highlightId)
        .setParameter("viewer", HeardSql.viewer(viewerId))
        .setParameter("now", Instant.now())
        .getResultList();
  }

  @Override
  public int deleteAllByHighlightId(Long highlightId) {
    return jpa.deleteAllByHighlightId(highlightId);
  }

  @Override
  public Map<Long, Long> countByHighlightIds(Collection<Long> highlightIds) {
    if (highlightIds.isEmpty()) return Map.of();
    return jpa.countGroupedByHighlightId(highlightIds).stream()
        .collect(
            Collectors.toMap(
                JpaPostHighlightReplyRepository.HighlightReplyCount::getHighlightId,
                JpaPostHighlightReplyRepository.HighlightReplyCount::getCnt));
  }
}
