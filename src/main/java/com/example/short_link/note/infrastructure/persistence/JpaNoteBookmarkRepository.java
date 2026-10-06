package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteBookmarkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaNoteBookmarkRepository extends JpaRepository<NoteBookmarkEntity, Long> {

  boolean existsByNoteIdAndUserId(Long noteId, Long userId);

  long deleteByNoteIdAndUserId(Long noteId, Long userId);
}
