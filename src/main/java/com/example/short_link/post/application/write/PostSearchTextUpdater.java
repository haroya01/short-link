package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 발행글의 파생 검색 컬럼(search_text)을 최신 상태로 유지한다. 제목·요약·태그·본문 블록 중 무엇이든 바뀌는 쓰기 경로에서 호출되어, 현재 블록을 읽어 평문으로
 * 펼친 뒤 관리 중인 {@link PostEntity} 에 도로 심는다(Hibernate dirty-check 가 커밋 때 flush). 검색 인덱스가 항상 본문과 일치하도록
 * 만드는 단일 지점.
 *
 * <p>블록을 다시 읽어야 하므로(메타만 바뀐 경우에도 본문은 그대로 담아야 한다) 한 번의 조회가 든다 — 편집은 드문 쓰기라 읽기 피드의 뜨거운 경로가 아니며, N+1 이
 * 아닌 단건 조회다.
 */
@Component
public class PostSearchTextUpdater {

  private final PostBlockRepository postBlockRepository;
  private final PostSearchTextFlattener flattener;

  public PostSearchTextUpdater(PostBlockRepository postBlockRepository, JsonMapper json) {
    this.postBlockRepository = postBlockRepository;
    this.flattener = new PostSearchTextFlattener(json);
  }

  /** 넘겨받은 블록 목록으로 곧장 계산한다(본문 교체 직후처럼 블록을 이미 손에 쥔 경우 — 중복 조회 회피). */
  public void refresh(PostEntity post, List<PostBlockEntity> blocks) {
    post.updateSearchText(
        flattener.flatten(post.getTitle(), post.getExcerpt(), post.getTags(), blocks));
  }

  /** 저장된 블록을 다시 읽어 계산한다(제목·요약·태그만 바뀐 경우처럼 블록이 손에 없을 때). */
  public void refresh(PostEntity post) {
    refresh(post, postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()));
  }
}
