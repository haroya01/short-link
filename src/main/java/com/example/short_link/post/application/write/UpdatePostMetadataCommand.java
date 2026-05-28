package com.example.short_link.post.application.write;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * PATCH 의미. 각 필드 null = 변경 안 함. 빈 문자열 = 의도적 clear (excerpt / ogImage 만). languageTag / title / slug
 * 는 빈 문자열 invalid. tags 는 null = 변경 안 함, 빈 리스트 = 전체 삭제 (정규화는 도메인 PostEntity.updateTags).
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

  private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
  private static final Set<String> ALLOWED_LANGUAGES = Set.of("ko", "ja", "en");

  public UpdatePostMetadataCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (postId == null) throw new IllegalArgumentException("postId required");
    if (title != null) {
      if (title.isBlank()) throw new IllegalArgumentException("title cannot be blank");
      if (title.length() > 200) throw new IllegalArgumentException("title max 200");
    }
    if (slug != null) {
      if (slug.length() < 2 || slug.length() > 200) {
        throw new IllegalArgumentException("slug length 2~200");
      }
      if (!SLUG_PATTERN.matcher(slug).matches()) {
        throw new IllegalArgumentException("slug invalid format");
      }
    }
    if (excerpt != null && excerpt.length() > 500) {
      throw new IllegalArgumentException("excerpt max 500");
    }
    if (languageTag != null && !languageTag.isBlank() && !ALLOWED_LANGUAGES.contains(languageTag)) {
      throw new IllegalArgumentException("languageTag must be one of: ko, ja, en");
    }
  }
}
