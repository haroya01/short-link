package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.BookmarkFolderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaBookmarkFolderRepository extends JpaRepository<BookmarkFolderEntity, Long> {

  Optional<BookmarkFolderEntity> findByIdAndUserId(Long id, Long userId);

  List<BookmarkFolderEntity> findAllByUserIdOrderByCreatedAtAsc(Long userId);

  boolean existsByUserIdAndName(Long userId, String name);
}
