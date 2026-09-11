package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.example.short_link.post.application.read.PublicFeedView;
import com.example.short_link.post.presentation.request.PublicFeedRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 없이 접근하는 전역 공개 피드 (blog.kurl.me 홈). 모든 작가의 발행글을 최신/트렌딩으로 둘러본다. permitAll 는 SecurityConfig 의 GET
 * /api/v1/public/** 가 커버.
 */
@RestController
@RequestMapping("/api/v1/public/posts")
@RequiredArgsConstructor
public class PublicFeedController {

  private final PublicFeedQueryService publicFeedQueryService;

  @GetMapping
  public PublicFeedView feed(
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String lang,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    PublicFeedRequest request =
        PublicFeedRequest.builder().sort(sort).tag(tag).q(q).lang(lang).build();
    return publicFeedQueryService.feed(request.toQuery(page, size));
  }
}
