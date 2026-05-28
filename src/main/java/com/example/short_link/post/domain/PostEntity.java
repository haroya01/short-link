package com.example.short_link.post.domain;

import com.example.short_link.common.jpa.BaseTimeEntity;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

  public void publish() {
    if (status == PostStatus.PUBLISHED) {
      return;
    }
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
}
