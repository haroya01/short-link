package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.BookmarkFolderEntity;
import com.example.short_link.post.domain.repository.BookmarkFolderRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class BookmarkFolderRepositoryAdapter implements BookmarkFolderRepository {

  private final JpaBookmarkFolderRepository jpa;

  @Override
  public BookmarkFolderEntity save(BookmarkFolderEntity folder) {
    return jpa.save(folder);
  }

  @Override
  public Optional<BookmarkFolderEntity> findByIdAndUserId(Long id, Long userId) {
    return jpa.findByIdAndUserId(id, userId);
  }

  @Override
  public List<BookmarkFolderEntity> findAllByUserIdOrderByCreatedAtAsc(Long userId) {
    return jpa.findAllByUserIdOrderByCreatedAtAsc(userId);
  }

  @Override
  public boolean existsByUserIdAndName(Long userId, String name) {
    return jpa.existsByUserIdAndName(userId, name);
  }

  @Override
  public void delete(BookmarkFolderEntity folder) {
    jpa.delete(folder);
  }
}
