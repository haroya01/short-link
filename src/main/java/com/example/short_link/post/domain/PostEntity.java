package com.example.short_link.post.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(
    name = "posts",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_posts_user_slug",
            columnNames = {"user_id", "slug"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostEntity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 200)
  private String slug;

  @Column(nullable = false, length = 200)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PostStatus status = PostStatus.DRAFT;

  @Column(name = "language_tag", nullable = false, length = 16)
  private String languageTag = "ko";

  @Column(name = "published_at")
  private Instant publishedAt;

  /**
   * 내용 편집 시 {@link #markEdited()}로만 갱신한다. 조회·좋아요 쓰기에 바뀌지 않도록 Hibernate 자동 수정 시각을 사용하지 않는다. 최초 편집
   * 전에는 null이다.
   */
  @Column(name = "last_edited_at")
  private Instant lastEditedAt;

  @Column(name = "scheduled_at")
  private Instant scheduledAt;

  @Column(length = 500)
  private String excerpt;

  @Column(name = "og_image_url", length = 512)
  private String ogImageUrl;

  /** S3/R2 object key for custom OG override. Auto-generated OG images don't set this. */
  @Column(name = "og_image_key", length = 256)
  private String ogImageKey;

  @Column(name = "view_count", nullable = false)
  private long viewCount = 0L;

  /** Denormalized like (공감) counter; the post_like table is the source of truth for uniqueness. */
  @Column(name = "like_count", nullable = false)
  private long likeCount = 0L;

  public static final int MAX_TAGS = 10;
  public static final int MAX_TAG_LENGTH = 40;

  /** 작성자 순서를 유지하며 정규화는 {@link #updateTags}에서 수행한다. */
  @ElementCollection
  @CollectionTable(name = "post_tag", joinColumns = @JoinColumn(name = "post_id"))
  @OrderColumn(name = "ordinal")
  @Column(name = "tag", length = MAX_TAG_LENGTH, nullable = false)
  @BatchSize(size = 50)
  private List<String> tags = new ArrayList<>();

  /** 공개 상태를 우회하는 미리보기 권한이다. 처음 요청할 때 생성하고 이후 유지한다. */
  @Column(name = "preview_token", length = 64)
  private String previewToken;

  /** Optional series membership. seriesOrder is the 0-based position within the series. */
  @Column(name = "series_id")
  private Long seriesId;

  @Column(name = "series_order")
  private Integer seriesOrder;

  /** 고정 순서(0부터). null은 고정되지 않은 글이다. */
  @Column(name = "pin_order")
  private Integer pinOrder;

  public PostEntity(Long userId, String slug, String title, String languageTag) {
    this.userId = userId;
    this.slug = slug;
    this.title = title;
    this.languageTag = languageTag;
    this.status = PostStatus.DRAFT;
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }

  public boolean isDraft() {
    return status == PostStatus.DRAFT;
  }

  public boolean isScheduled() {
    return status == PostStatus.SCHEDULED;
  }

  public boolean isPublished() {
    return status == PostStatus.PUBLISHED;
  }

  public boolean isUnpublished() {
    return status == PostStatus.UNPUBLISHED;
  }

  public boolean isPublic() {
    return status == PostStatus.PUBLISHED;
  }

  public void updateTitle(String title) {
    this.title = title;
  }

  /** Slug is frozen once the post has ever been public (published or unpublished). */
  public void updateSlug(String slug) {
    if (status == PostStatus.PUBLISHED || status == PostStatus.UNPUBLISHED) {
      throw new PostException(PostErrorCode.SLUG_FROZEN, this.slug);
    }
    this.slug = slug;
  }

  public void updateExcerpt(String excerpt) {
    this.excerpt = excerpt;
  }

  public void updateOgImage(String url, String key) {
    this.ogImageUrl = url;
    this.ogImageKey = key;
  }

  public void clearOgImage() {
    this.ogImageUrl = null;
    this.ogImageKey = null;
  }

  public void updateLanguageTag(String languageTag) {
    this.languageTag = languageTag;
  }

  public void markEdited() {
    this.lastEditedAt = Instant.now();
  }

  private void requireTitleToGoPublic() {
    if (title == null || title.isBlank()) {
      throw new PostException(PostErrorCode.TITLE_REQUIRED);
    }
  }

  public void publish() {
    if (status == PostStatus.PUBLISHED) {
      return;
    }
    requireTitleToGoPublic();
    this.status = PostStatus.PUBLISHED;
    if (this.publishedAt == null) {
      this.publishedAt = Instant.now();
    }
    this.scheduledAt = null;
  }

  public void schedule(Instant when) {
    if (when == null || !when.isAfter(Instant.now())) {
      throw new PostException(PostErrorCode.SCHEDULE_IN_PAST);
    }
    if (status == PostStatus.PUBLISHED || status == PostStatus.UNPUBLISHED) {
      throw new PostException(PostErrorCode.SCHEDULE_AFTER_PUBLISH);
    }
    requireTitleToGoPublic();
    this.status = PostStatus.SCHEDULED;
    this.scheduledAt = when;
  }

  public void unpublish() {
    if (status != PostStatus.PUBLISHED) {
      throw new PostException(PostErrorCode.UNPUBLISH_NOT_PUBLISHED);
    }
    this.status = PostStatus.UNPUBLISHED;
  }

  /** 재공개할 때 URL과 최초 발행 시각은 유지한다. */
  public void republish() {
    if (status != PostStatus.UNPUBLISHED) {
      throw new PostException(PostErrorCode.REPUBLISH_NOT_UNPUBLISHED);
    }
    this.status = PostStatus.PUBLISHED;
  }

  public void backToDraft() {
    if (status != PostStatus.SCHEDULED) {
      throw new PostException(PostErrorCode.BACK_TO_DRAFT_NOT_SCHEDULED);
    }
    this.status = PostStatus.DRAFT;
    this.scheduledAt = null;
  }

  public void incrementViewCount() {
    this.viewCount++;
  }

  public void incrementLikeCount() {
    this.likeCount++;
  }

  /** 대소문자 중복은 첫 표기를 유지한다. 빈 입력은 태그 전체 삭제다. */
  public void updateTags(List<String> raw) {
    this.tags.clear();
    this.tags.addAll(normalizeTags(raw));
  }

  public String ensurePreviewToken(String token) {
    if (this.previewToken == null) {
      this.previewToken = token;
    }
    return this.previewToken;
  }

  public void assignToSeries(Long seriesId, int order) {
    this.seriesId = seriesId;
    this.seriesOrder = order;
  }

  public void clearSeries() {
    this.seriesId = null;
    this.seriesOrder = null;
  }

  public void pinAt(int order) {
    this.pinOrder = order;
  }

  public void clearPin() {
    this.pinOrder = null;
  }

  public static List<String> normalizeTags(List<String> raw) {
    if (raw == null || raw.isEmpty()) return List.of();
    Map<String, String> byLowercase = new LinkedHashMap<>();
    for (String candidate : raw) {
      if (candidate == null) continue;
      String trimmed = candidate.trim();
      if (trimmed.isEmpty()) continue;
      if (trimmed.length() > MAX_TAG_LENGTH) {
        trimmed = trimmed.substring(0, MAX_TAG_LENGTH).trim();
      }
      if (trimmed.isEmpty()) continue;
      byLowercase.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
      if (byLowercase.size() >= MAX_TAGS) break;
    }
    return new ArrayList<>(byLowercase.values());
  }
}
