package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostRevisionEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostRevisionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostRevisionCapture {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final PostRevisionRepository postRevisionRepository;
  private final PostBlockRepository postBlockRepository;

  public PostRevisionEntity capture(PostEntity post) {
    List<PostBlockEntity> blocks =
        postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId());
    PostSnapshot snapshot =
        new PostSnapshot(
            post.getTitle(),
            post.getExcerpt(),
            post.getOgImageUrl(),
            post.getOgImageKey(),
            post.getLanguageTag(),
            blocks.stream()
                .map(b -> new PostSnapshot.BlockSnapshot(b.getType().name(), b.getContent()))
                .toList());
    int nextVersion =
        postRevisionRepository
            .findLatestByPostId(post.getId())
            .map(r -> r.getVersionNumber() + 1)
            .orElse(1);
    return postRevisionRepository.save(
        new PostRevisionEntity(post.getId(), nextVersion, post.getTitle(), writeJson(snapshot)));
  }

  private String writeJson(PostSnapshot snapshot) {
    try {
      return OBJECT_MAPPER.writeValueAsString(snapshot);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize PostSnapshot", e);
    }
  }
}
