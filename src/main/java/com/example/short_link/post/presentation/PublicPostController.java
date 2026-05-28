package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PublicPostDetail;
import com.example.short_link.post.application.read.PublicPostListView;
import com.example.short_link.post.application.read.PublicPostQueryService;
import lombok.RequiredArgsConstructor;
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

  @GetMapping("/{username}/posts")
  public PublicPostListView listPublicPosts(@PathVariable String username) {
    return publicPostQueryService.listPublicPosts(username);
  }

  @GetMapping("/{username}/posts/{slug}")
  public PublicPostDetail findPublicPost(@PathVariable String username, @PathVariable String slug) {
    return publicPostQueryService.findPublicPost(username, slug);
  }
}
