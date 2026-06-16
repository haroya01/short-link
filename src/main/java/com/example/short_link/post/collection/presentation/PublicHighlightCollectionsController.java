package com.example.short_link.post.collection.presentation;

import com.example.short_link.post.collection.application.read.CollectionQueryService;
import com.example.short_link.post.collection.application.read.CollectionSummaryView;
import com.example.short_link.post.collection.domain.ConnectionBlockType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "이 문장이 속한 길" — 한 하이라이트를 담은 *공개* 컬렉션/길 목록(미로그인 독자도 본다). A 척추의 발견 고리: 한 문장에서 그것이 엮인 길들로. 비공개·링크공유
 * 컬렉션은 빠진다.
 */
@RestController
@RequestMapping("/api/v1/public/highlights")
@RequiredArgsConstructor
public class PublicHighlightCollectionsController {

  private final CollectionQueryService queryService;

  @GetMapping("/{id}/collections")
  public List<CollectionSummaryView> collectionsContaining(@PathVariable Long id) {
    return queryService.publicCollectionsContaining(ConnectionBlockType.HIGHLIGHT, id);
  }
}
