package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteRepostEntity;
import java.util.List;
import java.util.Optional;

public interface NoteRepostRepository {

  Optional<NoteRepostEntity> addIfAbsent(Long noteId, Long userId);

  Optional<NoteRepostEntity> delete(Long noteId, Long userId);

  // Nothing across a block with the reposter; no repost of a note whose author is blocked either
  // way, or of someone else's note whose author the viewer muted.
  List<Long> recentNoteIdsByUser(Long userId, Long viewerId, int offset, int limit);
}
