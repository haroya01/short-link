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
 * "이 글이 속한 길" — 한 글을 담은 *공개* 컬렉션/길 목록(미로그인 독자도 본다). 하이라이트판(GET
 * /public/highlights/{id}/collections)을 글 타입으로 미러링한 것. 비공개·링크공유 컬렉션은 빠진다.
 */
@RestController
@RequestMapping("/api/v1/public/posts")
@RequiredArgsConstructor
public class PublicPostCollectionsController {

  private final CollectionQueryService queryService;

  @GetMapping("/{id}/collections")
  public List<CollectionSummaryView> collectionsContaining(@PathVariable Long id) {
    return queryService.publicCollectionsContaining(ConnectionBlockType.POST, id);
  }
}
