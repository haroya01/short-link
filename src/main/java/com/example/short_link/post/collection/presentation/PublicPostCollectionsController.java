package com.example.short_link.post.collection.presentation;

import com.example.short_link.post.collection.application.read.CollectionQueryService;
import com.example.short_link.post.collection.application.read.CollectionSummaryView;
import com.example.short_link.post.collection.application.read.PostCollectionsView;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "이 글이 속한 길" — 한 글을 담은 *공개* 컬렉션/길 목록(미로그인 독자도 본다). 하이라이트판(GET
 * /public/highlights/{id}/collections)을 글 타입으로 미러링한 것. 비공개·링크공유 컬렉션은 빠진다. 배치판(GET
 * /public/posts/collections?ids=…)은 피드가 보이는 카드 id 를 모아 한 번에 물어 "소속 한 올"을 N+1 없이 켜기 위함이다.
 */
@RestController
@RequestMapping("/api/v1/public/posts")
@RequiredArgsConstructor
public class PublicPostCollectionsController {

  /** 한 요청에 조회할 글 id 상한 — 피드 한 화면 분량. 넘치면 앞의 이만큼만 본다(방어). */
  static final int MAX_IDS = 50;

  private final CollectionQueryService queryService;

  @GetMapping("/{id}/collections")
  public List<CollectionSummaryView> collectionsContaining(@PathVariable Long id) {
    return queryService.publicCollectionsContaining(ConnectionBlockType.POST, id);
  }

  /**
   * 여러 글이 각각 속한 *공개* 컬렉션들 — 요청 순서대로(각 글 안은 최근순). 없는·빈 id 는 빈 목록으로 채워 응답에 요청한 모든 id 가 있게 한다.
   * 상한(MAX_IDS) 을 넘는 뒤쪽 id 는 잘라 방어한다. GET /api/v1/public/** 은 permitAll — 미로그인 독자도 본다.
   */
  @GetMapping("/collections")
  public List<PostCollectionsView> collectionsForPosts(@RequestParam List<Long> ids) {
    List<Long> capped = ids.stream().distinct().limit(MAX_IDS).toList();
    Map<Long, List<CollectionSummaryView>> byPost =
        queryService.publicCollectionsContainingBatch(ConnectionBlockType.POST, capped);
    return capped.stream()
        .map(id -> new PostCollectionsView(id, byPost.getOrDefault(id, List.of())))
        .toList();
  }
}
