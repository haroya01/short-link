package com.example.short_link.post.application.write;

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

/**
 * 이전 발행 스냅샷으로 본문 + 메타데이터 복원. slug / status / publishedAt 등 상태 메타는 건드리지 않음 — 본문 (블록) 과 가벼운 메타
 * (title, excerpt, ogImage, languageTag) 만 되돌림.
 */
@Service
@RequiredArgsConstructor
public class RestorePostRevisionUseCase {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final PostOwnership postOwnership;
  private final PostRepository postRepository;
  private final PostRevisionRepository postRevisionRepository;
  private final PostBlockRepository postBlockRepository;

  @Transactional
  public PostEntity execute(RestorePostRevisionCommand cmd) {
    PostEntity post = postOwnership.requireOwned(cmd.userId(), cmd.postId());
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
      post.updateOgImage(snapshot.ogImageUrl(), snapshot.ogImageKey());
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
      postBlockRepository.saveAll(blocks);
    }

    post.markEdited();
    return postRepository.save(post);
  }

  private PostSnapshot readJson(String json) {
    try {
      return OBJECT_MAPPER.readValue(json, PostSnapshot.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to deserialize PostSnapshot", e);
    }
  }
}
