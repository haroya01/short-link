package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.repository.PostNoteQuoteRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostNoteQuoteRepositoryAdapter implements PostNoteQuoteRepository {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void replace(Long postId, Collection<Long> noteIds) {
    jdbcTemplate.update("DELETE FROM post_note_quote WHERE post_id = ?", postId);
    add(postId, noteIds);
  }

  @Override
  public void add(Long postId, Collection<Long> noteIds) {
    if (noteIds.isEmpty()) {
      return;
    }
    List<Object> args = new ArrayList<>(noteIds.size() * 2);
    for (Long noteId : noteIds) {
      args.add(postId);
      args.add(noteId);
    }
    jdbcTemplate.update(
        "INSERT INTO post_note_quote (post_id, note_id) VALUES "
            + String.join(", ", Collections.nCopies(noteIds.size(), "(?, ?)")),
        args.toArray());
  }
}
