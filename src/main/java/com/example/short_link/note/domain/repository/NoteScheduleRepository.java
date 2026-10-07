package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteScheduleEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NoteScheduleRepository {

  List<NoteScheduleEntity> byUser(Long userId);

  Optional<NoteScheduleEntity> owned(Long id, Long userId);

  Optional<NoteScheduleEntity> findById(Long id);

  record Load(long pending, long onDay) {}

  Load load(Long userId, Instant dayStart, Instant dayEnd);

  List<Long> due(Instant now, int limit);

  NoteScheduleEntity save(NoteScheduleEntity schedule);

  void delete(NoteScheduleEntity schedule);

  int deleteAllForUser(Long userId);
}
