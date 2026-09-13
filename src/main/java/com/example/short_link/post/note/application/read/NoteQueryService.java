package com.example.short_link.post.note.application.read;

import com.example.short_link.post.note.domain.NoteRow;
import com.example.short_link.post.note.domain.repository.NoteLikeRepository;
import com.example.short_link.post.note.domain.repository.NoteRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
    int safePage = Math.max(page, 0);
    int safeSize = Math.clamp(size, 1, MAX_SIZE);
    List<NoteRow> rows = notes.feed(safePage, safeSize);
    boolean hasNext = (long) (safePage + 1) * safeSize < notes.countAll();
    return new NoteFeedView(rows, safePage, hasNext);
  }

  /** 공개 피드는 사용자별 likedByMe를 포함하지 않으므로 인증 상태를 별도로 조회한다. */
  @Transactional(readOnly = true)
  public List<Long> likedNoteIds(Long userId, List<Long> noteIds) {
    return likes.likedNoteIds(userId, noteIds);
  }
}
