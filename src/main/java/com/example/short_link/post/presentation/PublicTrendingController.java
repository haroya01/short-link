package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.example.short_link.post.application.read.TrendingTagSection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 없이 접근하는 공개 피드의 "주제별 인기" 섹션 (blog.kurl.me 홈 인기 탭). 인기 태그별로 상위 글을 묶어 반환. permitAll 은
 * SecurityConfig 의 GET /api/v1/public/** 가 커버.
 */
@RestController
@RequestMapping("/api/v1/public/feed")
@RequiredArgsConstructor
public class PublicTrendingController {

  private static final int MAX_TAGS = 20;
  private static final int MAX_PER_TAG = 20;

  private final PublicFeedQueryService publicFeedQueryService;

  @GetMapping("/trending-by-tag")
  public List<TrendingTagSection> trendingByTag(
      @RequestParam(defaultValue = "6") int tagLimit,
      @RequestParam(defaultValue = "8") int perTag) {
    int safeTagLimit = Math.min(Math.max(tagLimit, 1), MAX_TAGS);
    int safePerTag = Math.min(Math.max(perTag, 1), MAX_PER_TAG);
    return publicFeedQueryService.trendingByTag(safeTagLimit, safePerTag);
  }
}
