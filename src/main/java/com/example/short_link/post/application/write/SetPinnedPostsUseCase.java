package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostStatus;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Author curation — replace the caller's pinned-post set. The author picks which of their PUBLISHED
 * posts surface first on their public profile and in what order.
 */
@Service
@RequiredArgsConstructor
public class SetPinnedPostsUseCase {

  private final PostRepository postRepository;

  /**
   * Set {@code orderedPostIds} as the pinned set (0-based pin_order = list index). Only the
   * caller's own PUBLISHED posts are pinnable; unknown / unowned / non-published ids are ignored.
   * Posts not in the list have their pin cleared. Idempotent.
   */
  @Transactional
  public void execute(Long userId, List<Long> orderedPostIds) {
    List<Long> requested = orderedPostIds == null ? List.of() : orderedPostIds;
    List<PostEntity> published =
        postRepository.findAllByUserIdAndStatusOrderByPublishedAtDesc(userId, PostStatus.PUBLISHED);
    for (PostEntity post : published) {
      int idx = requested.indexOf(post.getId());
      if (idx >= 0) post.pinAt(idx);
      else post.clearPin();
      postRepository.save(post);
    }
  }
}
