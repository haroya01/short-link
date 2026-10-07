package com.example.short_link.abuse.infrastructure.persistence;

import com.example.short_link.abuse.domain.AbuseReportEntity;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader.CommentSubjectSnapshot;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader.LinkSubjectSnapshot;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader.NoteSubjectSnapshot;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader.PostSubjectSnapshot;
import com.example.short_link.abuse.domain.repository.AbuseSubjectReader.UserSubjectSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface JpaAbuseSubjectQueries extends Repository<AbuseReportEntity, Long> {
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

  @Query(
      value =
          "SELECT l.id AS subjectId, l.short_code AS shortCode, l.original_url AS originalUrl, "
              + "u.username AS ownerHandle, "
              + "CASE WHEN m.link_id IS NULL THEN 0 ELSE 1 END AS disabled "
              + "FROM link l LEFT JOIN users u ON u.id = l.user_id "
              + "LEFT JOIN link_moderation m ON m.link_id = l.id "
              + "WHERE l.id IN (:linkIds)",
      nativeQuery = true)
  List<LinkSubjectSnapshot> findLinkSubjectSnapshots(@Param("linkIds") Collection<Long> linkIds);

  @Query(value = "SELECT id FROM link WHERE short_code = :shortCode", nativeQuery = true)
  Optional<Long> findLinkIdByShortCode(@Param("shortCode") String shortCode);

  @Query(value = "SELECT COUNT(*) FROM link WHERE id = :id", nativeQuery = true)
  long countLinkById(@Param("id") Long id);

  // A note from another server is named by its account there, user@server.
  @Query(
      value =
          "SELECT n.id AS subjectId, SUBSTRING(n.body, 1, 200) AS excerpt, "
              + "COALESCE(u.username, CONCAT(r.username, '@', r.domain)) AS authorHandle "
              + "FROM note n LEFT JOIN users u ON u.id = n.user_id "
              + "LEFT JOIN federation_remote_actor r ON r.id = n.remote_actor_id "
              + "WHERE n.id IN (:noteIds)",
      nativeQuery = true)
  List<NoteSubjectSnapshot> findNoteSubjectSnapshots(@Param("noteIds") Collection<Long> noteIds);

  @Query(value = "SELECT COUNT(*) FROM note WHERE id = :id", nativeQuery = true)
  long countNoteById(@Param("id") Long id);

  @Query(value = "SELECT COUNT(*) FROM posts WHERE id = :id", nativeQuery = true)
  long countPostById(@Param("id") Long id);

  @Query(value = "SELECT COUNT(*) FROM comment WHERE id = :id", nativeQuery = true)
  long countCommentById(@Param("id") Long id);

  @Query(value = "SELECT COUNT(*) FROM users WHERE id = :id", nativeQuery = true)
  long countUserById(@Param("id") Long id);
}
