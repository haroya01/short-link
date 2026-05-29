package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostEntity;
import java.time.Instant;
import java.util.List;

/**
 * Public-safe post 메타데이터 (홈페이지 글 목록 + detail header). id 는 abuse report subjectId 등 stable
 * reference 위해 노출 (slug 가 영구지만 client 가 typed reference 쓸 수 있게). 내부 필드 (status / scheduledAt 등) 제외.
 */
public record PublicPostListItem(
    Long id,
    String slug,
    String title,
    String excerpt,
    String ogImageUrl,
    String languageTag,
    List<String> tags,
    long likeCount,
    Instant publishedAt) {

  public static PublicPostListItem from(PostEntity post) {
    return new PublicPostListItem(
        post.getId(),
        post.getSlug(),
        post.getTitle(),
        post.getExcerpt(),
        post.getOgImageUrl(),
        post.getLanguageTag(),
        List.copyOf(post.getTags()),
        post.getLikeCount(),
        post.getPublishedAt());
  }
}
