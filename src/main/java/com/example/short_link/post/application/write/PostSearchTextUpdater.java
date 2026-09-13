package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostSearchTextRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 제목·요약·태그·본문을 바꾸는 쓰기 경로에서 검색 평문을 갱신한다. 피드와 상세 조회가 큰 평문까지 로드하지 않도록 별도 테이블에 저장한다. */
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
  }

  public void refresh(PostEntity post) {
    refresh(post, postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()));
  }
}
