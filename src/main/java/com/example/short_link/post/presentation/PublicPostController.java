package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PostScoped;
import com.example.short_link.post.application.read.PublicPostDetail;
import com.example.short_link.post.application.read.PublicPostListView;
import com.example.short_link.post.application.read.PublicPostQueryService;
import com.example.short_link.post.application.write.MarkdownBlocksConverter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/profiles")
@RequiredArgsConstructor
public class PublicPostController {

  private final PublicPostQueryService publicPostQueryService;
  private final MarkdownBlocksConverter markdownBlocks;

  @GetMapping("/{username}/posts")
  public ResponseEntity<PublicPostListView> listPublicPosts(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable String username,
      HttpServletRequest req) {
    return PostResponses.list(
        publicPostQueryService.listPublicPosts(username, PostReaderHeaders.from(viewerId, req)));
  }

  @GetMapping("/{username}/posts/{slug}")
  public ResponseEntity<PublicPostDetail> findPublicPost(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable String username,
      @PathVariable String slug,
      @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
      HttpServletRequest req) {
    return PostResponses.of(
        publicPostQueryService.findPublicPost(
            username, slug, PostReaderHeaders.from(viewerId, req), acceptLanguage));
  }

  @GetMapping(value = "/{username}/posts/{slug}/markdown", produces = "text/markdown;charset=UTF-8")
  public ResponseEntity<String> publicMarkdown(
      @AuthenticationPrincipal Long viewerId,
      @PathVariable String username,
      @PathVariable String slug,
      HttpServletRequest req) {
    PostScoped<PublicPostDetail> detail =
        publicPostQueryService.findReadablePost(
            username, slug, PostReaderHeaders.from(viewerId, req));
    String markdown = markdownBlocks.toMarkdown(detail.body().blocks());
    return PostResponses.restricted(ResponseEntity.ok(), detail.restricted())
        .header("Content-Disposition", "inline; filename=\"" + slug + ".md\"")
        .body(markdown);
  }
}
