package com.example.short_link.post.collection.presentation;

import com.example.short_link.post.collection.application.read.CollectionQueryService;
import com.example.short_link.post.collection.application.read.CollectionSummaryView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 한 큐레이터의 *공개* 컬렉션/길 목록 — 작가 홈의 "컬렉션" 탭이 읽는다(미로그인 독자도 본다). 연결 그래프의 발견 고리를 닫는다: 한 길의 큐레이터에서 그가 엮은 다른
 * 길들로. 비공개·링크공유는 빠진다.
 */
@RestController
@RequestMapping("/api/v1/public/profiles")
@RequiredArgsConstructor
public class PublicProfileCollectionsController {

  private final CollectionQueryService queryService;

  @GetMapping("/{username}/collections")
  public List<CollectionSummaryView> publicCollections(@PathVariable String username) {
    return queryService.listPublicByUsername(username);
  }
}
