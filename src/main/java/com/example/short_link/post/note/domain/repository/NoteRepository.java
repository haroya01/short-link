package com.example.short_link.post.note.domain.repository;

import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.NoteRow;
import java.util.List;
import java.util.Optional;

public interface NoteRepository {

  NoteEntity save(NoteEntity note);

  Optional<NoteEntity> findById(Long id);

  void delete(NoteEntity note);

  /** 최신순 한 페이지 — hasNext 판정은 호출측이 {@link #countAll()} 과 조합한다. */
  List<NoteRow> feed(int page, int size);

  long countAll();

  Optional<NoteRow> findRowById(Long id);
}
