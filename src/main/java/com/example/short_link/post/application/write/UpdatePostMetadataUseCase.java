package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.image.ImageVariantKeys;
import com.example.short_link.common.image.ImageVariantsRequested;
import com.example.short_link.common.live.LiveRevoke;
import com.example.short_link.common.live.LiveTopic;
import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.ProfilePathSlugs;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
  private final PostAccessSettings accessSettings;
  private final ProfileCacheInvalidator profileCache;
  private final ApplicationEventPublisher events;

  @Transactional
  public PostView execute(UpdatePostMetadataCommand cmd) {
    PostEntity post = postOwnership.requireOwnedForUpdate(cmd.userId(), cmd.postId());
    if (cmd.access().any() && !cmd.changesContent()) {
      editGuard.checkAccessChange(post);
      changeAccess(post, cmd);
      return writeViews.fromSaved(postRepository.save(post));
    }
    editGuard.check(post, cmd.baseVersion(), cmd.overwrite());
    if (cmd.access().any()) {
      changeAccess(post, cmd);
    }

    applySlug(post, cmd);
    SearchFields before = SearchFields.of(post, cmd.tags() != null);
    applyFields(post, cmd);
    post.markEdited();
    refreshSearchAndRevision(post, before.changedIn(post));
    return writeViews.fromSaved(postRepository.save(post));
  }

  private record SearchFields(String title, String excerpt, List<String> tags) {

    static SearchFields of(PostEntity post, boolean tagsAsked) {
      return new SearchFields(
          post.getTitle(), post.getExcerpt(), tagsAsked ? List.copyOf(post.getTags()) : null);
    }

    boolean changedIn(PostEntity post) {
      return !Objects.equals(title, post.getTitle())
          || !Objects.equals(excerpt, post.getExcerpt())
          || (tags != null && !tags.equals(post.getTags()));
    }
  }

  private void applyFields(PostEntity post, UpdatePostMetadataCommand cmd) {
    if (cmd.title() != null) {
      post.updateTitle(cmd.title());
    }
    if (cmd.excerpt() != null) {
      post.updateExcerpt(cmd.excerpt().isBlank() ? null : cmd.excerpt());
    }
    applyCover(post, cmd);
    if (cmd.languageTag() != null && !cmd.languageTag().isBlank()) {
      post.updateLanguageTag(cmd.languageTag());
    }
    if (cmd.tags() != null) {
      post.updateTags(cmd.tags());
    }
  }

  private void applySlug(PostEntity post, UpdatePostMetadataCommand cmd) {
    if (cmd.slug() == null || cmd.slug().equals(post.getSlug())) {
      return;
    }
    if (ProfilePathSlugs.isReserved(cmd.slug())) {
      throw new PostException(PostErrorCode.SLUG_RESERVED, cmd.slug()).with("slug", cmd.slug());
    }
    if (postRepository.existsByUserIdAndSlug(cmd.userId(), cmd.slug())) {
      throw new PostException(PostErrorCode.SLUG_CONFLICT, cmd.slug());
    }
    post.updateSlug(cmd.slug());
  }

  private void applyCover(PostEntity post, UpdatePostMetadataCommand cmd) {
    if (cmd.ogImageUrl() == null) {
      return;
    }
    if (cmd.ogImageUrl().isBlank()) {
      post.clearOgImage();
      return;
    }
    boolean newCover = !cmd.ogImageUrl().equals(post.getOgImageUrl());
    post.updateOgImage(cmd.ogImageUrl(), cmd.ogImageKey(), coverChosen(post, cmd));
    List<String> cover = ImageVariantKeys.keysIn(cmd.ogImageUrl());
    if (newCover && !cover.isEmpty()) {
      events.publishEvent(new ImageVariantsRequested(cover));
    }
  }

  private void refreshSearchAndRevision(PostEntity post, boolean searchFieldChanged) {
    if (!post.isPublished()) {
      return;
    }
    List<PostBlockEntity> body = searchFieldChanged ? searchTextUpdater.refresh(post) : null;
    if (body == null) {
      revisions.capture(post);
    } else {
      revisions.capture(post, body);
    }
  }

  private void changeAccess(PostEntity post, UpdatePostMetadataCommand cmd) {
    accessSettings.apply(post, cmd.access());
    events.publishEvent(new LiveRevoke.Recheck(LiveTopic.post(cmd.postId())));
    if (post.isPublished()) {
      profileCache.evictByUserId(post.getUserId());
    }
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
