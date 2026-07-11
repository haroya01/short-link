package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.PostBlockEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostBlockRepository;
import com.example.short_link.post.domain.repository.PostSearchTextRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 발행글의 파생 검색 평문(post_search_text 곁 테이블)을 최신 상태로 유지한다. 제목·요약·태그·본문 블록 중 무엇이든 바뀌는 쓰기 경로에서 호출되어, 현재
 * 블록을 읽어 평문으로 펼친 뒤 곁 테이블에 upsert 한다. 검색 인덱스가 항상 본문과 일치하도록 만드는 단일 지점.
 *
 * <p>평문은 posts 컬럼이 아니라 별도 테이블에 담는다 — 읽기 경로(피드·상세)가 PostEntity 를 로드할 때 수십 KB 본문 평문을 함께 끌어오지 않도록. 검색만
 * 필요할 때 네이티브 쿼리가 JOIN 으로 붙는다.
 *
 * <p>블록을 다시 읽어야 하므로(메타만 바뀐 경우에도 본문은 그대로 담아야 한다) 한 번의 조회가 든다 — 편집은 드문 쓰기라 읽기 피드의 뜨거운 경로가 아니며, N+1 이
 * 아닌 단건 조회다.
 */
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

  /** 넘겨받은 블록 목록으로 곧장 계산한다(본문 교체 직후처럼 블록을 이미 손에 쥔 경우 — 중복 조회 회피). */
  public void refresh(PostEntity post, List<PostBlockEntity> blocks) {
    String flattened =
        flattener.flatten(post.getTitle(), post.getExcerpt(), post.getTags(), blocks);
    postSearchTextRepository.upsert(post.getId(), flattened);
  }

  /** 저장된 블록을 다시 읽어 계산한다(제목·요약·태그만 바뀐 경우처럼 블록이 손에 없을 때). */
  public void refresh(PostEntity post) {
    refresh(post, postBlockRepository.findAllByPostIdOrderByBlockOrderAsc(post.getId()));
  }
}
