package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.FolderBookmarkCount;
import com.example.short_link.post.domain.PostBookmarkEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaPostBookmarkRepository extends JpaRepository<PostBookmarkEntity, Long> {

  boolean existsByPostIdAndUserId(Long postId, Long userId);

  Optional<PostBookmarkEntity> findByPostIdAndUserId(Long postId, Long userId);

  List<PostBookmarkEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  @Query(
      "select new com.example.short_link.post.domain.FolderBookmarkCount(b.folderId, count(b)) "
          + "from PostBookmarkEntity b "
          + "where b.userId = :userId and b.folderId is not null "
          + "group by b.folderId")
  List<FolderBookmarkCount> countByFolder(@Param("userId") Long userId);

  // MySQL INSERT IGNORE: the (post_id, user_id) unique key turns a duplicate bookmark into a no-op
  // (0 rows) instead of a constraint violation, so the bookmark flow stays idempotent without
  // catching an exception inside the transaction. folder_id defaults to NULL (unfiled); created_at
  // is set here because the native insert bypasses the @CreationTimestamp callback.
  @Modifying
  @Query(
      value =
          "INSERT IGNORE INTO post_bookmark (post_id, user_id, created_at) "
              + "VALUES (:postId, :userId, NOW())",
      nativeQuery = true)
  int insertIgnore(@Param("postId") Long postId, @Param("userId") Long userId);

  @Modifying
  @Query("delete from PostBookmarkEntity b where b.postId = :postId")
  int deleteAllByPostId(@Param("postId") Long postId);
}
