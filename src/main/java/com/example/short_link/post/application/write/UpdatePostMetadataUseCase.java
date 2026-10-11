package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.ProfilePathSlugs;
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
  private final PostEditGuard editGuard;
  private final PostRepository postRepository;
  private final PostSearchTextUpdater searchTextUpdater;
  private final PostRevisionCapture revisions;
  private final PostWriteViewAssembler writeViews;

  @Transactional
  public PostView execute(UpdatePostMetadataCommand cmd) {
    PostEntity post = postOwnership.requireOwnedForUpdate(cmd.userId(), cmd.postId());
    editGuard.check(post, cmd.baseVersion(), cmd.overwrite());

    if (cmd.slug() != null && !cmd.slug().equals(post.getSlug())) {
      if (ProfilePathSlugs.isReserved(cmd.slug())) {
        throw new PostException(PostErrorCode.SLUG_RESERVED, cmd.slug()).with("slug", cmd.slug());
      }
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
        post.updateOgImage(cmd.ogImageUrl(), cmd.ogImageKey(), coverChosen(post, cmd));
      }
    }
    if (cmd.languageTag() != null && !cmd.languageTag().isBlank()) {
      post.updateLanguageTag(cmd.languageTag());
    }
    if (cmd.tags() != null) {
      post.updateTags(cmd.tags());
    }

    post.markEdited();
    List<PostBlockEntity> body = searchFieldChanged ? searchTextUpdater.refresh(post) : null;
    if (post.isPublished()) {
      if (body == null) {
        revisions.capture(post);
      } else {
        revisions.capture(post, body);
      }
    }
    return writeViews.fromSaved(postRepository.save(post));
  }

  // coverChosen 을 보내지 않는 옛 클라이언트는 본문 첫 이미지도 표지로 자동 저장한다. 같은 표지면 그대로 두고, 새 표지는
  // 고르지 않은 것으로 본다.
  private static boolean coverChosen(PostEntity post, UpdatePostMetadataCommand cmd) {
    if (cmd.coverChosen() != null) {
      return cmd.coverChosen();
    }
    return cmd.ogImageUrl().equals(post.getOgImageUrl()) && post.isCoverChosen();
  }

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
