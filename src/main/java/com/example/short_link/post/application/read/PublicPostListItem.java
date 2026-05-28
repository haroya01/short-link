package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import java.time.Instant;

/** Public-safe post 메타데이터 (홈페이지 글 목록 + detail header). 내부 필드 (scheduledAt 등) 제외. */
public record PublicPostListItem(
    String slug,
    String title,
    String excerpt,
    String ogImageUrl,
    String languageTag,
    Instant publishedAt) {

  public static PublicPostListItem from(PostEntity post) {
    return new PublicPostListItem(
        post.getSlug(),
        post.getTitle(),
        post.getExcerpt(),
        post.getOgImageUrl(),
        post.getLanguageTag(),
        post.getPublishedAt());
  }
}
