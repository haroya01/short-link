package com.example.short_link.note.domain.repository;

import com.example.short_link.note.domain.NoteMediaEntity;
import java.util.Collection;
import java.util.List;

public interface NoteMediaRepository {

  List<NoteMediaEntity> saveAll(List<NoteMediaEntity> media);

  List<NoteMediaEntity> findByNoteIds(Collection<Long> noteIds);

  void deleteAllByNoteId(Long noteId);
}
