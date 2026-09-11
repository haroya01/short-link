package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UpdatePostMetadataUseCase {

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;
  private final PostSearchTextUpdater searchTextUpdater;
  private final PostWriteViewAssembler writeViews;

  @Transactional
  public PostView execute(UpdatePostMetadataCommand cmd) {
    PostEntity post = postOwnership.requireOwnedForUpdate(cmd.userId(), cmd.postId());

    if (cmd.slug() != null && !cmd.slug().equals(post.getSlug())) {
      if (postRepository.existsByUserIdAndSlug(cmd.userId(), cmd.slug())) {
        throw new PostException(PostErrorCode.SLUG_CONFLICT, cmd.slug());
      }
      post.updateSlug(cmd.slug());
    }
    boolean searchFieldChanged = cmd.title() != null || cmd.excerpt() != null || cmd.tags() != null;
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
    if (searchFieldChanged) {
      searchTextUpdater.refresh(post);
    }
    return writeViews.fromSaved(postRepository.save(post));
  }

  /** 관리자 권한은 HTTP 보안 계층에서 검사한다. adminUserId는 감사 로그용이다. */
  @Transactional
  public PostView adminExecute(Long adminUserId, Long postId, String title, List<String> tags) {
    log.info("admin post metadata edit: adminUserId={}, postId={}", adminUserId, postId);
    PostEntity post =
        postRepository
            .findByIdForUpdate(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    boolean searchFieldChanged = title != null || tags != null;
    if (title != null) {
      post.updateTitle(title);
    }
    if (tags != null) {
      post.updateTags(tags);
    }
    post.markEdited();
    if (searchFieldChanged) {
      searchTextUpdater.refresh(post);
    }
    return writeViews.fromSaved(postRepository.save(post));
  }
}
