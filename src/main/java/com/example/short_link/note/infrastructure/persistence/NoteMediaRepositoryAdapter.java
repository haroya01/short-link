package com.example.short_link.note.infrastructure.persistence;

import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NoteMediaRepositoryAdapter implements NoteMediaRepository {

  private final JpaNoteMediaRepository jpa;

  @Override
  public List<NoteMediaEntity> saveAll(List<NoteMediaEntity> media) {
    return media.isEmpty() ? List.of() : jpa.saveAll(media);
  }

  @Override
  public List<NoteMediaEntity> findByNoteIds(Collection<Long> noteIds) {
    return noteIds.isEmpty() ? List.of() : jpa.findByNoteIdInOrderByNoteIdAscPositionAsc(noteIds);
  }

  @Override
  public void deleteAllByNoteId(Long noteId) {
    jpa.deleteAllByNoteId(noteId);
  }
}
