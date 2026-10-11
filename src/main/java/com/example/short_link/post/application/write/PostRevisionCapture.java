package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostRevisionEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostRevisionCapture {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final PostRevisionRepository postRevisionRepository;
  private final PostBlockRepository postBlockRepository;

  public void capture(PostEntity post) {
    capture(post, postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()));
  }

  // 마지막 리비전과 내용이 같으면 새 버전을 만들지 않는다.
  public void capture(PostEntity post, List<PostBlockEntity> blocks) {
    PostSnapshot snapshot =
        new PostSnapshot(
            post.getTitle(),
            post.getExcerpt(),
            post.getOgImageUrl(),
            post.getOgImageKey(),
            post.isCoverChosen(),
            post.getLanguageTag(),
            blocks.stream()
                .map(b -> new PostSnapshot.BlockSnapshot(b.getType().name(), b.getContent()))
                .toList());
    Optional<PostRevisionEntity> latest = postRevisionRepository.findLatestByPostId(post.getId());
    if (latest.isPresent() && snapshot.sameContentAs(readJson(latest.get().getContentJson()))) {
      return;
    }
    int nextVersion = latest.map(r -> r.getVersionNumber() + 1).orElse(1);
    postRevisionRepository.save(
        new PostRevisionEntity(post.getId(), nextVersion, post.getTitle(), writeJson(snapshot)));
  }

  private static PostSnapshot readJson(String json) {
    if (json == null) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readValue(json, PostSnapshot.class);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  private String writeJson(PostSnapshot snapshot) {
    try {
      return OBJECT_MAPPER.writeValueAsString(snapshot);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize PostSnapshot", e);
    }
  }
}
