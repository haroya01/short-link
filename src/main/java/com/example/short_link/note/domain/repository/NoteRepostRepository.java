package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteRepostEntity;
import java.util.List;
import java.util.Optional;

public interface NoteRepostRepository {

  Optional<NoteRepostEntity> addIfAbsent(Long noteId, Long userId);

  Optional<NoteRepostEntity> delete(Long noteId, Long userId);

  List<Long> recentNoteIdsByUser(Long userId, int offset, int limit);
}
