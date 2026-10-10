package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.PostView;
import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostBlockType;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostRevisionEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestorePostRevisionUseCase {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final PostOwnership postOwnership;
  private final PostEditGuard editGuard;
  private final PostRepository postRepository;
  private final PostRevisionRepository postRevisionRepository;
  private final PostBlockRepository postBlockRepository;
  private final PostSearchTextUpdater searchTextUpdater;
  private final PostNoteQuotes noteQuotes;
  private final PostWriteViewAssembler writeViews;

  @Transactional
  public PostView execute(RestorePostRevisionCommand cmd) {
    PostEntity post = postOwnership.requireOwnedForUpdate(cmd.userId(), cmd.postId());
    editGuard.check(post, null, false);
    PostRevisionEntity revision =
        postRevisionRepository
            .findByPostIdAndVersionNumber(cmd.postId(), cmd.versionNumber())
            .orElseThrow(
                () -> new PostException(PostErrorCode.REVISION_NOT_FOUND, cmd.versionNumber()));

    PostSnapshot snapshot = readJson(revision.getContentJson());

    post.updateTitle(snapshot.title());
    post.updateExcerpt(snapshot.excerpt());
    if (snapshot.ogImageUrl() == null) {
      post.clearOgImage();
    } else {
      post.updateOgImage(
          snapshot.ogImageUrl(), snapshot.ogImageKey(), restoredCoverChosen(post, snapshot));
    }
    if (snapshot.languageTag() != null) {
      post.updateLanguageTag(snapshot.languageTag());
    }

    postBlockRepository.deleteAllByPostId(cmd.postId());
    if (!snapshot.blocks().isEmpty()) {
      List<PostBlockEntity> blocks = new ArrayList<>(snapshot.blocks().size());
      int order = 0;
      for (PostSnapshot.BlockSnapshot bs : snapshot.blocks()) {
        blocks.add(
            new PostBlockEntity(
                cmd.postId(), PostBlockType.valueOf(bs.type()), bs.content(), order++));
      }
      postBlockRepository.insertAll(blocks);
    }

    post.markEdited();
    noteQuotes.index(post, searchTextUpdater.refresh(post));
    return writeViews.fromSaved(postRepository.save(post));
  }

  // coverChosen 이 생기기 전의 리비전에는 값이 없다. 지금 표지와 같으면 지금 상태를, 다르면 고르지 않은 것으로 둔다.
  private static boolean restoredCoverChosen(PostEntity post, PostSnapshot snapshot) {
    if (snapshot.coverChosen() != null) {
      return snapshot.coverChosen();
    }
    return snapshot.ogImageUrl().equals(post.getOgImageUrl()) && post.isCoverChosen();
  }

  private PostSnapshot readJson(String json) {
    try {
      return OBJECT_MAPPER.readValue(json, PostSnapshot.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize PostSnapshot", e);
    }
  }
}
