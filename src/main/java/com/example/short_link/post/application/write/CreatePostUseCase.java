package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatePostUseCase {

  private final PostRepository postRepository;

  @Transactional
  public PostEntity execute(CreatePostCommand cmd) {
    if (postRepository.existsByUserIdAndSlug(cmd.userId(), cmd.slug())) {
      throw new PostException(PostErrorCode.SLUG_CONFLICT, cmd.slug())
          .with("userId", cmd.userId())
          .with("slug", cmd.slug());
    }
    PostEntity post = new PostEntity(cmd.userId(), cmd.slug(), cmd.title(), cmd.languageTag());
    return postRepository.save(post);
  }
}
