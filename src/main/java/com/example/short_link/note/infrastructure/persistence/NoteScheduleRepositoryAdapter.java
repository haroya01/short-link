package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteScheduleEntity;
import com.example.short_link.note.domain.repository.NoteScheduleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NoteScheduleRepositoryAdapter implements NoteScheduleRepository {

  private final JpaNoteScheduleRepository jpa;

  @Override
  public List<NoteScheduleEntity> byUser(Long userId) {
    return jpa.findByUserIdOrderByPublishAtAsc(userId);
  }

  @Override
  public Optional<NoteScheduleEntity> owned(Long id, Long userId) {
    return jpa.findByIdAndUserId(id, userId);
  }

  @Override
  public Optional<NoteScheduleEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public Load load(Long userId, Instant dayStart, Instant dayEnd) {
    Object[] row = jpa.load(userId, dayStart, dayEnd).getFirst();
    return new Load(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
  }

  @Override
  public List<Long> due(Instant now, int limit) {
    return jpa.findDue(now, PageRequest.of(0, limit));
  }

  @Override
  public NoteScheduleEntity save(NoteScheduleEntity schedule) {
    return jpa.save(schedule);
  }

  @Override
  public void delete(NoteScheduleEntity schedule) {
    jpa.delete(schedule);
  }

  @Override
  public int deleteAllForUser(Long userId) {
    return jpa.deleteAllForUser(userId);
  }
}
