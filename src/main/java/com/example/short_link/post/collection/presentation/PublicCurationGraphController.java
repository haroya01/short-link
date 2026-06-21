package com.example.short_link.post.collection.presentation;

import com.example.short_link.post.collection.application.read.CurationGraphQueryService;
import com.example.short_link.post.collection.application.read.KindredCuratorView;
import com.example.short_link.post.collection.application.read.RelatedBlockView;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 큐레이션 그래프(공개) — 발견의 두 홉을 연다(미로그인 독자도 본다). 한 블록 → 같은 길에 함께 놓인 블록("이것과 이어진 것"), 한 큐레이터 → 취향이 겹치는
 * 큐레이터("같은 것을 엮은 사람"). 사람이 손으로 엮은 공개 연결만 — 알고리즘 랭킹이 아니라 큐레이션으로 잇는다(§0).
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicCurationGraphController {

  private final CurationGraphQueryService graph;

  /** 이 블록과 같은 공개 컬렉션에 함께 놓인 블록들. blockType = post|highlight|note. */
  @GetMapping("/graph/blocks/{blockType}/{refId}/related")
  public List<RelatedBlockView> related(
      @PathVariable String blockType,
      @PathVariable Long refId,
      @RequestParam(defaultValue = "12") int limit) {
    ConnectionBlockType type = parseType(blockType);
    if (type == null) return List.of();
    return graph.relatedTo(type, refId, limit);
  }

  /** 이 큐레이터와 취향이 겹치는(같은 것을 엮은) 다른 큐레이터들. */
  @GetMapping("/profiles/{username}/kindred")
  public List<KindredCuratorView> kindred(
      @PathVariable String username, @RequestParam(defaultValue = "12") int limit) {
    return graph.kindredCurators(username, limit);
  }

  private static ConnectionBlockType parseType(String raw) {
    try {
      return ConnectionBlockType.valueOf(raw.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException e) {
      return null; // 모르는 종류는 조용히 빈 결과(§0 — 바깥은 조용히).
    }
  }
}
