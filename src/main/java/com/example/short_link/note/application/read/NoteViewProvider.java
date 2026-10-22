package com.example.short_link.note.application.read;

import com.example.short_link.common.note.NoteViewReader;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.RemotePoolRow;
import com.example.short_link.note.domain.repository.NoteRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  @Override
  @Transactional(readOnly = true)
  public List<Listed> received(Long viewerId, Long beforeId, int limit) {
    return listed(notes.federatedBefore(viewerId, beforeId, limit), viewerId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Listed> latest(Long viewerId, Long beforeId, int limit) {
    return listed(notes.latestBefore(viewerId, beforeId, limit), viewerId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Pooled> remotePool(Long viewerId, Instant since, Instant until, int limit) {
    List<RemotePoolRow> rows = notes.remotePool(viewerId, since, until, limit);
    Map<Long, List<String>> tags = notes.tagsOf(rows.stream().map(RemotePoolRow::id).toList());
    return rows.stream()
        .map(
            row ->
                new Pooled(
                    row.id(),
                    row.remoteActorId(),
                    row.domain(),
                    row.language(),
                    row.createdAt(),
                    row.uri(),
                    tags.getOrDefault(row.id(), List.of())))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Listed> listed(Long viewerId, List<Long> noteIds) {
    if (noteIds.isEmpty()) {
      return List.of();
    }
    Map<Long, NoteEntity> byId = new HashMap<>();
    notes.findAllByIdIn(noteIds).forEach(note -> byId.put(note.getId(), note));
    List<NoteEntity> page = noteIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    return listed(page, viewerId);
  }

  private List<Listed> listed(List<NoteEntity> page, Long viewerId) {
    Map<Long, String> uris = new HashMap<>();
    page.forEach(note -> uris.put(note.getId(), note.getUri()));
    List<NoteView> shown = views.ofFeed(page, viewerId);
    return shown.stream().map(view -> new Listed(view.id(), view, uris.get(view.id()))).toList();
  }
}
