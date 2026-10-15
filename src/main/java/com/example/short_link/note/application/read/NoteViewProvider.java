package com.example.short_link.note.application.read;

import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class NoteViewProvider implements NoteViewReader {

  private final NoteRepository notes;
  private final NoteViews views;

  @Override
  @Transactional(readOnly = true)
  public Optional<Object> view(Long noteId, Long viewerId) {
    return notes
        .findUnblocked(noteId, viewerId)
        .flatMap(note -> views.ofFeed(List.of(note), viewerId).stream().findFirst());
  }
}
