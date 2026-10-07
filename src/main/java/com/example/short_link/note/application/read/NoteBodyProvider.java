package com.example.short_link.note.application.read;

import com.example.short_link.common.note.NoteBlock;
import com.example.short_link.common.note.NoteBodyReader;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.repository.NotePeopleReader;
import com.example.short_link.note.domain.repository.NoteRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
// Collections are public, so only public and unlisted notes can be filed in one or shown from one.
class NoteBodyProvider implements NoteBodyReader {

  private final NoteRepository notes;
  private final NotePeopleReader people;

  @Override
  public Map<Long, NoteBlock> blocksByIds(Collection<Long> noteIds) {
    List<NoteEntity> found =
        notes.findAllByIdIn(noteIds).stream()
            .filter(note -> note.getVisibility().shareable() && !note.isRemote())
            .toList();
    if (found.isEmpty()) return Map.of();
    Map<Long, NoteAuthor> authors =
        people.activeAuthors(found.stream().map(NoteEntity::getUserId).collect(Collectors.toSet()));
    Map<Long, NoteBlock> blocks = new HashMap<>();
    for (NoteEntity note : found) {
      NoteAuthor author = authors.get(note.getUserId());
      if (author == null) continue;
      blocks.put(note.getId(), new NoteBlock(note.getId(), note.getBody(), author.username()));
    }
    return blocks;
  }

  @Override
  public boolean exists(Long noteId) {
    return notes
        .findById(noteId)
        .filter(note -> note.getVisibility().shareable() && !note.isRemote())
        .isPresent();
  }
}
