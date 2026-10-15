package com.example.short_link.post.application.write;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.user.UserModerationGuard;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.ImportedPostRepository;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImportedPostWriter {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz";

  private final PostRepository posts;
  private final ImportedPostRepository importedPosts;
  private final PostBlockRepository blocks;
  private final UserModerationGuard moderation;
  private final PostNumbering numbering;
  private final PostSearchTextUpdater searchText;
  private final PostRevisionCapture revisions;
  private final PostNoteQuotes noteQuotes;
  private final ProfileCacheInvalidator cache;
  private final DeletePostUseCase deletePost;
  private final Clock clock;

  @Transactional
  public Optional<Long> begin(BeginImportedPostCommand cmd) {
    moderation.requireCanWrite(cmd.userId());
    if (cmd.resumePostId() != null) {
      Optional<PostEntity> existing = posts.findById(cmd.resumePostId());
      if (existing.isPresent()) {
        PostEntity post = existing.get();
        return post.isOwnedBy(cmd.userId()) && post.isImported() && post.isDraft()
            ? Optional.of(post.getId())
            : Optional.empty();
      }
    }
    PostEntity draft =
        PostEntity.importedDraft(
            cmd.userId(), draftSlug(), cmd.importSourceId(), cmd.platform(), cmd.sourceUrl());
    PostEntity saved = posts.save(draft);
    posts.flush();
    return Optional.of(saved.getId());
  }

  @Transactional
  public boolean publish(PublishImportedPostCommand cmd) {
    PostEntity post = posts.findByIdForUpdate(cmd.postId()).orElse(null);
    if (post == null
        || !post.isOwnedBy(cmd.userId())
        || !post.isImported()
        || !post.isDraft()
        || post.isTakenDown()) {
      return false;
    }
    moderation.requireCanWrite(cmd.userId());
    post.updateTitle(cmd.title());
    post.updateExcerpt(cmd.excerpt());
    post.updateLanguageTag(cmd.languageTag());
    post.updateTags(cmd.tags());
    if (cmd.coverUrl() == null) {
      post.clearOgImage();
    } else {
      post.updateOgImage(cmd.coverUrl(), cmd.coverKey(), cmd.coverChosen());
    }
    numbering.numberIfClientMade(post);
    post.publishImported(
        cmd.excerptOnly(),
        cmd.linkId(),
        cmd.shortCode(),
        cmd.hiddenFromDiscover(),
        cmd.originalPublishedAt(),
        clock.instant());
    posts.save(post);
    blocks.deleteAllByPostId(post.getId());
    List<PostBlockEntity> entities = new ArrayList<>(cmd.blocks().size());
    int order = 0;
    for (ReplacePostBlocksCommand.BlockInput input : cmd.blocks()) {
      entities.add(new PostBlockEntity(post.getId(), input.type(), input.content(), order++));
    }
    List<PostBlockEntity> persisted = List.of();
    if (!entities.isEmpty()) {
      blocks.insertAll(entities);
      persisted = blocks.findAllByPostIdOrderByBlockOrderAsc(post.getId());
    }
    searchText.refresh(post, persisted);
    revisions.capture(post, persisted);
    noteQuotes.indexFirstPublish(post, persisted);
    cache.evictByUserId(post.getUserId());
    return true;
  }

  @Transactional
  public int hideFromDiscover(Long importSourceId, boolean hidden) {
    return importedPosts.updateHiddenFromDiscover(importSourceId, hidden);
  }

  public int deleteAllFrom(Long userId, Long importSourceId) {
    int deleted = 0;
    for (Long postId : importedPosts.findIdsByImportSourceId(importSourceId)) {
      deletePost.execute(new DeletePostCommand(userId, postId));
      deleted++;
    }
    return deleted;
  }

  private static String draftSlug() {
    StringBuilder slug = new StringBuilder("draft-");
    for (int i = 0; i < 7; i++) {
      slug.append(BASE36.charAt(RANDOM.nextInt(BASE36.length())));
    }
    return slug.toString();
  }
}
