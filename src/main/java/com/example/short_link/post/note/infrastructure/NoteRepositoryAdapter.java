package com.example.short_link.post.note.infrastructure;

import com.example.short_link.post.note.application.read.NoteRow;
import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NoteRepositoryAdapter implements NoteRepository {

  private final JpaNoteRepository jpa;

  @Override
  public NoteEntity save(NoteEntity note) {
    return jpa.save(note);
  }

  @Override
  public Optional<NoteEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public void delete(NoteEntity note) {
    jpa.delete(note);
  }

  @Override
  public Page<NoteRow> feed(Pageable pageable) {
    return jpa.feed(pageable);
  }

  @Override
  public Optional<NoteRow> findRowById(Long id) {
    return jpa.findRowById(id);
  }
}
