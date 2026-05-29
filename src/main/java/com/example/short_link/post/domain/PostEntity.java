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

  public static final int MAX_TAGS = 20;
  public static final int MAX_TAG_LENGTH = 40;

  /**
   * Freeform tags, owner-ordered. Stored in a side table as an ordered collection (velog-style
   * tags). Normalization (trim / blank-drop / case-insensitive dedup / length + count caps) lives
   * in {@link #updateTags} so the invariant holds regardless of caller.
   */
  @ElementCollection
  @CollectionTable(name = "post_tag", joinColumns = @JoinColumn(name = "post_id"))
  @OrderColumn(name = "ordinal")
  @Column(name = "tag", length = MAX_TAG_LENGTH, nullable = false)
  @BatchSize(size = 50)
  private List<String> tags = new ArrayList<>();

  /** Optional series membership. seriesOrder is the 0-based position within the series. */
  @Column(name = "series_id")
  private Long seriesId;

  @Column(name = "series_order")
  private Integer seriesOrder;

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

  /** A title may be blank while drafting, but is required before a post goes public. */
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

  /** Bring an unpublished post back. URL is preserved, publishedAt is unchanged. */
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

  public void decrementLikeCount() {
    if (this.likeCount > 0) this.likeCount--;
  }

  /**
   * Replace all tags. Normalizes: trims, drops blanks, truncates each to {@link #MAX_TAG_LENGTH},
   * de-duplicates case-insensitively (first occurrence's casing wins), and caps the list at {@link
   * #MAX_TAGS}. An empty/blank input clears all tags.
   */
  public void updateTags(List<String> raw) {
    this.tags.clear();
    this.tags.addAll(normalizeTags(raw));
  }

  public void assignToSeries(Long seriesId, int order) {
    this.seriesId = seriesId;
    this.seriesOrder = order;
  }

  public void clearSeries() {
    this.seriesId = null;
    this.seriesOrder = null;
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
      byLowercase.putIfAbsent(trimmed.toLowerCase(), trimmed);
      if (byLowercase.size() >= MAX_TAGS) break;
    }
    return new ArrayList<>(byLowercase.values());
  }
}
