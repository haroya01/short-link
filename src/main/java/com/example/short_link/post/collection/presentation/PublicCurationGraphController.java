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

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicCurationGraphController {

  private final CurationGraphQueryService graph;

  @GetMapping("/graph/blocks/{blockType}/{refId}/related")
  public List<RelatedBlockView> related(
      @PathVariable String blockType,
      @PathVariable Long refId,
      @RequestParam(defaultValue = "12") int limit) {
    ConnectionBlockType type = parseType(blockType);
    if (type == null) return List.of();
    return graph.relatedTo(type, refId, limit);
  }

  @GetMapping("/profiles/{username}/kindred")
  public List<KindredCuratorView> kindred(
      @PathVariable String username, @RequestParam(defaultValue = "12") int limit) {
    return graph.kindredCurators(username, limit);
  }

  private static ConnectionBlockType parseType(String raw) {
    try {
      return ConnectionBlockType.valueOf(raw.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException e) {
      return null;
    }
  }
}
