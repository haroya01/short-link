package com.example.short_link.post.note.domain.repository;

import com.example.short_link.post.note.application.read.NoteRow;
import com.example.short_link.post.note.domain.NoteEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NoteRepository {

  NoteEntity save(NoteEntity note);

  Optional<NoteEntity> findById(Long id);

  void delete(NoteEntity note);

  Page<NoteRow> feed(Pageable pageable);

  Optional<NoteRow> findRowById(Long id);
}
