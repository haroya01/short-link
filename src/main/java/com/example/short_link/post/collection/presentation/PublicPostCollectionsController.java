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

@RestController
@RequestMapping("/api/v1/public/posts")
@RequiredArgsConstructor
public class PublicPostCollectionsController {

  static final int MAX_IDS = 50;

  private final CollectionQueryService queryService;

  @GetMapping("/{id}/collections")
  public List<CollectionSummaryView> collectionsContaining(@PathVariable Long id) {
    return queryService.publicCollectionsContaining(ConnectionBlockType.POST, id);
  }

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
