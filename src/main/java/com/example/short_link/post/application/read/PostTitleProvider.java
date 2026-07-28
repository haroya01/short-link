package com.example.short_link.post.application.read;

import com.example.short_link.common.post.PostTitleReader;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Post-side implementation of the neutral {@link PostTitleReader} port. One batched {@code IN (…)}
 * read, so labelling N attributed posts costs one query rather than N.
 */
@Component
@RequiredArgsConstructor
class PostTitleProvider implements PostTitleReader {

  private final PostRepository postRepository;

  @Override
  public Map<Long, String> findTitlesByIds(Collection<Long> postIds) {
    if (postIds == null || postIds.isEmpty()) return Map.of();
    Map<Long, String> titles = new LinkedHashMap<>();
    for (PostEntity post : postRepository.findAllByIdIn(postIds)) {
      if (post.getId() != null) titles.put(post.getId(), post.getTitle());
    }
    return titles;
  }
}
