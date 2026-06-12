package com.example.short_link.post.note.application.read;

import com.example.short_link.post.note.domain.repository.NoteLikeRepository;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteQueryService {

  private static final int MAX_SIZE = 50;

  private final NoteRepository notes;
  private final NoteLikeRepository likes;

  @Transactional(readOnly = true)
  public NoteFeedView feed(int page, int size) {
    int safeSize = Math.clamp(size, 1, MAX_SIZE);
    Page<NoteRow> result = notes.feed(PageRequest.of(Math.max(page, 0), safeSize));
    return new NoteFeedView(result.getContent(), result.getNumber(), result.hasNext());
  }

  /** likedByMe 는 공개 피드에 안 싣는다(#538 comment_like 와 같은 분리) — 배치로 따로 묻는다. */
  @Transactional(readOnly = true)
  public List<Long> likedNoteIds(Long userId, List<Long> noteIds) {
    return likes.likedNoteIds(userId, noteIds);
  }
}
