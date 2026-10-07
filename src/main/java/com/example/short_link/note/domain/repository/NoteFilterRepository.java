package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteFilterEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NoteFilterRepository {

  List<NoteFilterEntity> live(Long userId, Instant now);

  Optional<NoteFilterEntity> owned(Long id, Long userId);

  long count(Long userId);

  NoteFilterEntity save(NoteFilterEntity filter);

  void delete(NoteFilterEntity filter);
}
