package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuotingPostsQueryService {

  static final int SIZE = 20;

  private final PostRepository postRepository;
  private final PostFeedItemAssembler feedItemAssembler;

  public PublicFeedView ofNote(Long noteId, int page) {
    int current = Math.max(page, 0);
    List<PostEntity> rows =
        postRepository.findPublishedQuotingNote(noteId, current * SIZE, SIZE + 1);
    boolean hasNext = rows.size() > SIZE;
    return new PublicFeedView(
        feedItemAssembler.assemble(hasNext ? rows.subList(0, SIZE) : rows), current, SIZE, hasNext);
  }
}
