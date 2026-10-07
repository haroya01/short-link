package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteFilterEntity;
import com.example.short_link.note.domain.repository.NoteFilterRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NoteFilterRepositoryAdapter implements NoteFilterRepository {

  private final JpaNoteFilterRepository jpa;

  @Override
  public List<NoteFilterEntity> live(Long userId, Instant now) {
    return jpa.findLive(userId, now);
  }

  @Override
  public Optional<NoteFilterEntity> owned(Long id, Long userId) {
    return jpa.findByIdAndUserId(id, userId);
  }

  @Override
  public long count(Long userId) {
    return jpa.countByUserId(userId);
  }

  @Override
  public NoteFilterEntity save(NoteFilterEntity filter) {
    return jpa.save(filter);
  }

  @Override
  public void delete(NoteFilterEntity filter) {
    jpa.delete(filter);
  }
}
