package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.common.post.PostAccessSql;
import com.example.short_link.common.reply.ReplyPreview;
import com.example.short_link.common.user.HeardSql;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class CommentRepositoryAdapter implements CommentRepository {

  private final JpaCommentRepository jpa;

  @PersistenceContext private EntityManager em;

  @Override
  public CommentEntity save(CommentEntity comment) {
    return jpa.save(comment);
  }

  @Override
  public Optional<CommentEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public void delete(CommentEntity comment) {
    jpa.delete(comment);
  }

  @Override
  public List<CommentEntity> findAllByPostIdOrderByCreatedAtAsc(Long postId) {
    return jpa.findAllByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<CommentEntity> findHeardByPostId(Long postId, Long viewerId) {
    return em.createNativeQuery(
            "SELECT c.* FROM comment c WHERE c.post_id = :postId AND c.deleted_at IS NULL AND "
                + PostAccessSql.said("c")
                + HeardSql.heard("c.user_id")
                + " AND (c.parent_id IS NULL OR EXISTS (SELECT 1 FROM comment pc"
                + " WHERE pc.id = c.parent_id"
                + HeardSql.heard("pc.user_id")
                + "))"
                + " ORDER BY c.created_at ASC",
            CommentEntity.class)
        .setParameter("postId", postId)
        .setParameter("viewer", HeardSql.viewer(viewerId))
        .setParameter("now", Instant.now())
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<CommentEntity> findHeardWithTombstonesByPostId(Long postId, Long viewerId) {
    return em.createNativeQuery(
            "SELECT c.* FROM comment c WHERE c.post_id = :postId AND "
                + PostAccessSql.said("c")
                + " AND ((c.deleted_at IS NULL"
                + HeardSql.heard("c.user_id")
                + " AND (c.parent_id IS NULL OR EXISTS (SELECT 1 FROM comment pc"
                + " WHERE pc.id = c.parent_id AND (pc.deleted_at IS NOT NULL"
                + " OR (pc.deleted_at IS NULL"
                + HeardSql.heard("pc.user_id")
                + ")))))"
                + " OR (c.deleted_at IS NOT NULL AND c.parent_id IS NULL"
                + " AND EXISTS (SELECT 1 FROM comment r WHERE r.parent_id = c.id"
                + " AND r.deleted_at IS NULL"
                + HeardSql.heard("r.user_id")
                + ")))"
                + " ORDER BY c.created_at ASC",
            CommentEntity.class)
        .setParameter("postId", postId)
        .setParameter("viewer", HeardSql.viewer(viewerId))
        .setParameter("now", Instant.now())
        .getResultList();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ReplyPreview> latestHeard(Collection<Long> postIds, Long viewerId, int perPost) {
    if (postIds.isEmpty()) {
      return List.of();
    }
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT x.post_id, x.id, x.user_id, x.username, x.display_name, x.avatar_url,"
                    + " x.body FROM (SELECT c.post_id, c.id, c.body, c.created_at, u.id AS user_id,"
                    + " u.username, u.display_name, u.avatar_url, ROW_NUMBER() OVER (PARTITION BY"
                    + " c.post_id ORDER BY c.created_at DESC, c.id DESC) AS rn FROM comment c"
                    + " JOIN users u ON u.id = c.user_id AND u.deleted_at IS NULL"
                    + " WHERE c.post_id IN (:ids) AND c.deleted_at IS NULL AND "
                    + PostAccessSql.said("c")
                    + HeardSql.heard("c.user_id")
                    + " AND (c.parent_id IS NULL OR EXISTS (SELECT 1 FROM comment pc"
                    + " WHERE pc.id = c.parent_id"
                    + HeardSql.heard("pc.user_id")
                    + "))) x WHERE x.rn <= :per ORDER BY x.post_id, x.rn DESC")
            .setParameter("ids", postIds)
            .setParameter("per", perPost)
            .setParameter("viewer", HeardSql.viewer(viewerId))
            .setParameter("now", Instant.now())
            .getResultList();
    List<ReplyPreview> previews = new ArrayList<>(rows.size());
    for (Object[] row : rows) {
      previews.add(
          new ReplyPreview(
              ((Number) row[0]).longValue(),
              ((Number) row[1]).longValue(),
              new ReplyPreview.Author(
                  ((Number) row[2]).longValue(), (String) row[3], (String) row[4], (String) row[5]),
              (String) row[6]));
    }
    return previews;
  }

  @Override
  public List<CommentEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId) {
    return jpa.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
  }

  @Override
  public List<CommentEntity> findAllByParentId(Long parentId) {
    return jpa.findAllByParentId(parentId);
  }

  @Override
  public int deleteAllByPostId(Long postId) {
    return jpa.deleteAllByPostId(postId);
  }
}
