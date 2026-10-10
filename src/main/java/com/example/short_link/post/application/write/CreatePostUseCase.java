package com.example.short_link.post.application.write;

import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.ProfilePathSlugs;
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
  private final PostWriteViewAssembler writeViews;

  @Transactional
  public PostView execute(CreatePostCommand cmd) {
    moderationGuard.requireCanWrite(cmd.userId());
    String slug = cmd.slug();
    if (ProfilePathSlugs.isReserved(slug)) {
      slug = firstFreeSuffix(cmd.userId(), slug);
    } else if (postRepository.existsByUserIdAndSlug(cmd.userId(), slug)) {
      throw new PostException(PostErrorCode.SLUG_CONFLICT, slug)
          .with("userId", cmd.userId())
          .with("slug", slug);
    }
    // Title column is NOT NULL; a draft may be untitled, so coalesce a missing title to blank.
    String title = cmd.title() == null ? "" : cmd.title();
    PostEntity post = new PostEntity(cmd.userId(), slug, title, cmd.languageTag());
    return writeViews.fromSaved(postRepository.save(post));
  }

  private String firstFreeSuffix(Long userId, String reserved) {
    int suffix = 2;
    while (postRepository.existsByUserIdAndSlug(userId, reserved + "-" + suffix)) suffix++;
    return reserved + "-" + suffix;
  }
}
