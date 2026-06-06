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
public class UpdatePostMetadataUseCase {

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;

  @Transactional
  public PostEntity execute(UpdatePostMetadataCommand cmd) {
    PostEntity post = postOwnership.requireOwned(cmd.userId(), cmd.postId());

    if (cmd.slug() != null && !cmd.slug().equals(post.getSlug())) {
      if (postRepository.existsByUserIdAndSlug(cmd.userId(), cmd.slug())) {
        throw new PostException(PostErrorCode.SLUG_CONFLICT, cmd.slug());
      }
      // updateSlug 가 frozen 상태에서 PostException(SLUG_FROZEN) throw
      post.updateSlug(cmd.slug());
    }
    if (cmd.title() != null) {
      post.updateTitle(cmd.title());
    }
    if (cmd.excerpt() != null) {
      post.updateExcerpt(cmd.excerpt().isBlank() ? null : cmd.excerpt());
    }
    if (cmd.ogImageUrl() != null) {
      if (cmd.ogImageUrl().isBlank()) {
        post.clearOgImage();
      } else {
        post.updateOgImage(cmd.ogImageUrl(), cmd.ogImageKey());
      }
    }
    if (cmd.languageTag() != null && !cmd.languageTag().isBlank()) {
      post.updateLanguageTag(cmd.languageTag());
    }
    if (cmd.tags() != null) {
      post.updateTags(cmd.tags());
    }

    post.markEdited();
    return postRepository.save(post);
  }
}
