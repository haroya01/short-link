package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.FolderBookmarkCount;
import com.example.short_link.post.domain.PostBookmarkEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
