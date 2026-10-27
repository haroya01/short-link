package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostSearchTextRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PostSearchTextUpdater {

  private final PostBlockRepository postBlockRepository;
  private final PostSearchTextRepository postSearchTextRepository;
  private final PostSearchTextFlattener flattener;

  public PostSearchTextUpdater(
      PostBlockRepository postBlockRepository,
      PostSearchTextRepository postSearchTextRepository,
      JsonMapper json) {
    this.postBlockRepository = postBlockRepository;
    this.postSearchTextRepository = postSearchTextRepository;
    this.flattener = new PostSearchTextFlattener(json);
  }

  public void refresh(PostEntity post, List<PostBlockEntity> blocks) {
    String flattened =
        flattener.flatten(post.getTitle(), post.getExcerpt(), post.getTags(), blocks);
    postSearchTextRepository.upsert(post.getId(), flattened);
    post.measureBody(flattener.bodyText(blocks));
  }

  public List<PostBlockEntity> refresh(PostEntity post) {
    List<PostBlockEntity> blocks =
        postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId());
    refresh(post, blocks);
    return blocks;
  }
}
