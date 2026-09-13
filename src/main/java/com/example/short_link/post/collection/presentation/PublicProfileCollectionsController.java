package com.example.short_link.post.collection.presentation;

import com.example.short_link.post.collection.application.read.CollectionQueryService;
import com.example.short_link.post.collection.application.read.CollectionSummaryView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PUBLIC 컬렉션만 노출한다. PRIVATE와 UNLISTED는 제외한다. */
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
