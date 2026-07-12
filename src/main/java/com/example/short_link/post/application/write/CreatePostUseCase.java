package com.example.short_link.post.application.write;

import com.example.short_link.common.user.UserModerationGuard;
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
  private final UserModerationGuard moderationGuard;

  @Transactional
  public PostEntity execute(CreatePostCommand cmd) {
    // 제재(BANNED/현재 SUSPENDED) 유저는 콘텐츠를 만들 수 없다.
    moderationGuard.requireCanWrite(cmd.userId());
    if (postRepository.existsByUserIdAndSlug(cmd.userId(), cmd.slug())) {
      throw new PostException(PostErrorCode.SLUG_CONFLICT, cmd.slug())
          .with("userId", cmd.userId())
          .with("slug", cmd.slug());
    }
    // Title column is NOT NULL; a draft may be untitled, so coalesce a missing title to blank.
    String title = cmd.title() == null ? "" : cmd.title();
    PostEntity post = new PostEntity(cmd.userId(), cmd.slug(), title, cmd.languageTag());
    return postRepository.save(post);
  }
}
