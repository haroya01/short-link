package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.common.user.HeardSql;
import com.example.short_link.post.domain.DiscoveryQuality;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostHighlightRepositoryAdapter implements PostHighlightRepository {

  private static final String ON_POST =
      "SELECT h.* FROM post_highlight h JOIN posts p ON p.id = h.post_id WHERE ";

  private static final String HEARD_WRITERS =
      HeardSql.heard("h.user_id") + HeardSql.heard("p.user_id");

  private final JpaPostHighlightRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public PostHighlightEntity save(PostHighlightEntity highlight) {
    return jpa.save(highlight);
  }

  @Override
  public Optional<PostHighlightEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public List<PostHighlightEntity> findAllByIdIn(Collection<Long> ids) {
    return jpa.findAllByIdIn(ids);
  }

  @Override
  public void delete(PostHighlightEntity highlight) {
    jpa.delete(highlight);
  }

  @Override
  public List<PostHighlightEntity> findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(Long postId) {
    return jpa.findAllByPostIdOrderByBlockOrderAscStartOffsetAsc(postId);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PostHighlightEntity> findHeardByPostId(Long postId, Long viewerId) {
    return em.createNativeQuery(
            "SELECT h.* FROM post_highlight h WHERE h.post_id = :postId"
                + HeardSql.heard("h.user_id")
                + " ORDER BY h.block_order ASC, h.start_offset ASC",
            PostHighlightEntity.class)
        .setParameter("postId", postId)
        .setParameter("viewer", HeardSql.viewer(viewerId))
        .setParameter("now", Instant.now())
        .getResultList();
  }

  @Override
  public List<PostHighlightEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId) {
    return jpa.findAllByUserIdOrderByCreatedAtDesc(userId);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PostHighlightEntity> findByUserIdsOrderByCreatedAtDesc(
      Collection<Long> userIds, Long viewerId, int page, int size) {
    if (userIds.isEmpty()) {
      return List.of();
    }
    return em.createNativeQuery(
            ON_POST + "h.user_id IN (:userIds)" + HEARD_WRITERS + " ORDER BY h.created_at DESC",
            PostHighlightEntity.class)
        .setParameter("userIds", userIds)
        .setParameter("viewer", HeardSql.viewer(viewerId))
        .setParameter("now", Instant.now())
        .setFirstResult(page * size)
        .setMaxResults(size)
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<PostHighlightEntity> findRecentOnPublishedPosts(Long viewerId, int page, int size) {
    return em.createNativeQuery(
            ON_POST
                + "p.status = 'PUBLISHED' AND p.body_text_length >= :minBody"
                + HEARD_WRITERS
                + " ORDER BY h.created_at DESC",
            PostHighlightEntity.class)
        .setParameter("minBody", DiscoveryQuality.MIN_BODY_TEXT_LENGTH)
        .setParameter("viewer", HeardSql.viewer(viewerId))
        .setParameter("now", Instant.now())
        .setFirstResult(page * size)
        .setMaxResults(size)
        .getResultList();
  }

  @Override
  public int deleteAllByPostId(Long postId) {
    return jpa.deleteAllByPostId(postId);
  }
}
