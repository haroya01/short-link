package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PublicPostDetail;
import com.example.short_link.post.application.read.PublicPostListView;
import com.example.short_link.post.application.read.PublicPostQueryService;
import com.example.short_link.post.application.write.MarkdownBlocksConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/profiles")
@RequiredArgsConstructor
public class PublicPostController {

  private final PublicPostQueryService publicPostQueryService;
  private final MarkdownBlocksConverter markdownBlocks;

  @GetMapping("/{username}/posts")
  public PublicPostListView listPublicPosts(@PathVariable String username) {
    return publicPostQueryService.listPublicPosts(username);
  }

  @GetMapping("/{username}/posts/{slug}")
  public PublicPostDetail findPublicPost(@PathVariable String username, @PathVariable String slug) {
    return publicPostQueryService.findPublicPost(username, slug);
  }

  /** 발행글만 내보내며 작성자용 마크다운 API와 같은 변환기를 사용한다. */
  @GetMapping(value = "/{username}/posts/{slug}/markdown", produces = "text/markdown;charset=UTF-8")
  public ResponseEntity<String> publicMarkdown(
      @PathVariable String username, @PathVariable String slug) {
    PublicPostDetail detail = publicPostQueryService.findPublicPost(username, slug);
    String markdown = markdownBlocks.toMarkdown(detail.blocks());
    return ResponseEntity.ok()
        .header("Content-Disposition", "inline; filename=\"" + slug + ".md\"")
        .body(markdown);
  }
}
