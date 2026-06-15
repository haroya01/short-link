package com.example.short_link.post.note.infrastructure;

import com.example.short_link.post.note.domain.NoteEntity;
import com.example.short_link.post.note.domain.NoteRow;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
  public List<NoteEntity> findAllByIdIn(Collection<Long> ids) {
    return jpa.findAllByIdIn(ids);
  }

  @Override
  public void delete(NoteEntity note) {
    jpa.delete(note);
  }

  @Override
  public List<NoteRow> feed(int page, int size) {
    return jpa.feedRows(PageRequest.of(page, size));
  }

  @Override
  public long countAll() {
    return jpa.count();
  }

  @Override
  public Optional<NoteRow> findRowById(Long id) {
    return jpa.findRowById(id);
  }
}
