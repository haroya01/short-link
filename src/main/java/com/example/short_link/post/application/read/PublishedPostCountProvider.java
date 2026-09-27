package com.example.short_link.post.application.read;

import com.example.short_link.common.post.PublishedPostCountReader;
import com.example.short_link.post.domain.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PublishedPostCountProvider implements PublishedPostCountReader {

  private final PostRepository postRepository;

  @Override
  public long countPublishedByUserId(Long userId) {
    return postRepository.countPublishedByUserId(userId);
  }
}
