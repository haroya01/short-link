package com.example.short_link.post.application.write;

import java.util.List;

/**
 * PATCH 의미. 각 필드 null = 변경 안 함. 빈 문자열 = 의도적 clear (excerpt / ogImage 만). 빈 title 은 초안에서 허용하고, 빈
 * languageTag 는 변경하지 않는다. slug 는 빈 문자열 invalid. tags 는 null = 변경 안 함, 빈 리스트 = 전체 삭제 (정규화는 도메인
 * PostEntity.updateTags).
 */
public record UpdatePostMetadataCommand(
    Long userId,
    Long postId,
    String title,
    String slug,
    String excerpt,
    String ogImageUrl,
    String ogImageKey,
    String languageTag,
    List<String> tags) {

  public UpdatePostMetadataCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
    if (title != null && title.length() > 200) {
      throw new IllegalArgumentException("title max 200");
    }
    if (slug != null) {
      PostMetadataValidation.requireSlug(slug, "slug invalid format");
    }
    if (excerpt != null && excerpt.length() > 500) {
      throw new IllegalArgumentException("excerpt max 500");
    }
    if (languageTag != null && !languageTag.isBlank()) {
      PostMetadataValidation.requireLanguage(languageTag);
    }
  }
}
