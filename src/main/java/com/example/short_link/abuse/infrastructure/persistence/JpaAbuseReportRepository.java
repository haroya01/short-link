package com.example.short_link.abuse.infrastructure.persistence;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.AbuseSubjectType;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository.CommentSubjectSnapshot;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository.PostSubjectSnapshot;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository.UserSubjectSnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAbuseReportRepository extends JpaRepository<AbuseReportEntity, Long> {

  List<AbuseReportEntity> findAllByStatusOrderByCreatedAtDesc(AbuseReportStatus status);

  List<AbuseReportEntity> findAllByOrderByCreatedAtDesc();

  boolean existsByReporterUserIdAndSubjectTypeAndSubjectIdAndStatusIn(
      Long reporterUserId,
      AbuseSubjectType subjectType,
      Long subjectId,
      Collection<AbuseReportStatus> statuses);

  @Query(
      value =
          "SELECT p.id AS subjectId, p.title AS title, p.slug AS slug, p.status AS status, "
              + "u.username AS authorHandle "
              + "FROM posts p LEFT JOIN users u ON u.id = p.user_id "
              + "WHERE p.id IN (:postIds)",
      nativeQuery = true)
  List<PostSubjectSnapshot> findPostSubjectSnapshots(@Param("postIds") Collection<Long> postIds);

  @Query(
      value =
          "SELECT c.id AS subjectId, SUBSTRING(c.body, 1, 200) AS excerpt, "
              + "u.username AS authorHandle, "
              + "CASE WHEN c.deleted_at IS NULL THEN 0 ELSE 1 END AS deleted "
              + "FROM comment c LEFT JOIN users u ON u.id = c.user_id "
              + "WHERE c.id IN (:commentIds)",
      nativeQuery = true)
  List<CommentSubjectSnapshot> findCommentSubjectSnapshots(
      @Param("commentIds") Collection<Long> commentIds);

  @Query(
      value =
          "SELECT u.id AS subjectId, u.username AS handle, "
              + "u.moderation_status AS moderationStatus "
              + "FROM users u WHERE u.id IN (:userIds)",
      nativeQuery = true)
  List<UserSubjectSnapshot> findUserSubjectSnapshots(@Param("userIds") Collection<Long> userIds);

  @Query(value = "SELECT COUNT(*) FROM posts WHERE id = :id", nativeQuery = true)
  long countPostById(@Param("id") Long id);

  @Query(value = "SELECT COUNT(*) FROM comment WHERE id = :id", nativeQuery = true)
  long countCommentById(@Param("id") Long id);

  @Query(value = "SELECT COUNT(*) FROM users WHERE id = :id", nativeQuery = true)
  long countUserById(@Param("id") Long id);
}
