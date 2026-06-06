package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.BookmarkFolderEntity;
import java.util.List;
import java.util.Optional;

public interface BookmarkFolderRepository {

  BookmarkFolderEntity save(BookmarkFolderEntity folder);

  Optional<BookmarkFolderEntity> findByIdAndUserId(Long id, Long userId);

  /** The user's folders, oldest first — a stable order so the filter bar doesn't reshuffle. */
  List<BookmarkFolderEntity> findAllByUserIdOrderByCreatedAtAsc(Long userId);

  boolean existsByUserIdAndName(Long userId, String name);

  void delete(BookmarkFolderEntity folder);
}
