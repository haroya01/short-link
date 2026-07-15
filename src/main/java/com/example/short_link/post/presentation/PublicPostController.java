package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PostBlockView;
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

/**
 * 인증 없이 접근 가능. (U3) subdomain 모델에서 Vercel 의 Next.js 가 ISR 렌더 시 호출. `john.kurl.me/article-slug` →
 * Cloudflare Worker → Vercel → GET /api/v1/public/profiles/john/posts/article-slug.
 */
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

  /**
   * 발행글 본문을 표준 마크다운 그대로 — 외부 도구(에디터·백업·정적 사이트 생성기)용 공개 내보내기. 저장 원문이 곧 마크다운이라는 데이터 소유 약속의 공개면: 작성자용
   * GET /posts/{id}/markdown 과 같은 직렬화(MarkdownBlocksConverter)를 쓰므로 두 출력이 갈라지지 않는다. 발행글만 —
   * findPublicPost 가 비공개·초안이면 404 를 던진다.
   */
  @GetMapping(value = "/{username}/posts/{slug}/markdown", produces = "text/markdown;charset=UTF-8")
  public ResponseEntity<String> publicMarkdown(
      @PathVariable String username, @PathVariable String slug) {
    PublicPostDetail detail = publicPostQueryService.findPublicPost(username, slug);
    String markdown =
        markdownBlocks.toMarkdown(
            detail.blocks().stream()
                .map(b -> new PostBlockView(null, b.type(), b.content(), b.blockOrder()))
                .toList());
    return ResponseEntity.ok()
        .header("Content-Disposition", "inline; filename=\"" + slug + ".md\"")
        .body(markdown);
  }
}
