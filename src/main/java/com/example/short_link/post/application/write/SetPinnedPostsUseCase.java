package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SetPinnedPostsUseCase {

  private final PostRepository postRepository;

  /** 본인 공개 글만 목록 순서(0부터)로 고정한다. 없는 글·타인 글·비공개 글 ID는 무시하고, 목록에서 빠진 공개 글은 고정을 해제한다. */
  @Transactional
  public void execute(Long userId, List<Long> orderedPostIds) {
    List<Long> requested = orderedPostIds == null ? List.of() : orderedPostIds;
    List<PostEntity> published = postRepository.findPublishedByUserIdForUpdate(userId);
    for (PostEntity post : published) {
      int idx = requested.indexOf(post.getId());
      if (idx >= 0) post.pinAt(idx);
      else post.clearPin();
      postRepository.save(post);
    }
  }
}
