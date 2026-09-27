package com.example.short_link.post.note.domain.repository;

import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.NoteRow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NoteRepository {

  NoteEntity save(NoteEntity note);

  Optional<NoteEntity> findById(Long id);

  List<NoteEntity> findAllByIdIn(Collection<Long> ids);

  void delete(NoteEntity note);

  List<NoteRow> feed(int page, int size);

  long countAll();

  Optional<NoteRow> findRowById(Long id);
}
