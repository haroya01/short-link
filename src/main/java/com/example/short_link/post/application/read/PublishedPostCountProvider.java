package com.example.short_link.post.application.read;

import com.example.short_link.common.post.PublishedPostCountReader;
import com.example.short_link.post.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Post-side implementation of the neutral {@link PublishedPostCountReader} port. Delegates to the
 * post domain's published-count query so other slices (the public profile) can read it without
 * depending on the post domain directly.
 */
@Component
@RequiredArgsConstructor
class PublishedPostCountProvider implements PublishedPostCountReader {

  private final PostRepository postRepository;

  @Override
  public long countPublishedByUserId(Long userId) {
    return postRepository.countPublishedByUserId(userId);
  }
}
