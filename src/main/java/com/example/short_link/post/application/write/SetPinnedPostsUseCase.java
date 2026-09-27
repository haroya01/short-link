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
