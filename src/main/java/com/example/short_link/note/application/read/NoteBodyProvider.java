package com.example.short_link.note.application.read;

import com.example.short_link.common.note.NoteBodyReader;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NoteRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NoteBodyProvider implements NoteBodyReader {

  private final NoteRepository notes;

  @Override
  public Map<Long, String> bodiesByIds(Collection<Long> noteIds) {
    return notes.findAllByIdIn(noteIds).stream()
        .collect(Collectors.toMap(NoteEntity::getId, NoteEntity::getBody));
  }

  @Override
  public boolean exists(Long noteId) {
    return notes.findById(noteId).isPresent();
  }
}
