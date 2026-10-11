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

  // 내용 편집 시 markEdited()로만 갱신한다. 조회·좋아요 쓰기에 바뀌지 않도록 Hibernate 자동 수정 시각을 사용하지 않는다. 최초 편집 전에는 null이다.
  @Column(name = "last_edited_at")
  private Instant lastEditedAt;

  @Column(name = "scheduled_at")
  private Instant scheduledAt;

  // 있는 동안 작성자는 고칠 수 있어도 다시 공개할 수 없다. 관리자 해제만 지운다.
  @Column(name = "taken_down_at")
  private Instant takenDownAt;

  @Column(length = 500)
  private String excerpt;

  @Column(name = "og_image_url", length = 512)
  private String ogImageUrl;

  @Column(name = "og_image_key", length = 256)
  private String ogImageKey;

  // 작성자가 직접 고른 표지인가. 본문 첫 이미지를 자동으로 채운 표지는 공유 카드에만 쓰고 피드 썸네일로는 쓰지 않는다.
  @Column(name = "cover_chosen", nullable = false)
  private boolean coverChosen;

  @Column(name = "view_count", nullable = false)
  private long viewCount = 0L;

  // Denormalized like (공감) counter; the post_like table is the source of truth for uniqueness.
  @Column(name = "like_count", nullable = false)
  private long likeCount = 0L;

  public static final int MAX_TAGS = 10;
  public static final int MAX_TAG_LENGTH = 40;

  @ElementCollection
  @CollectionTable(name = "post_tag", joinColumns = @JoinColumn(name = "post_id"))
  @OrderColumn(name = "ordinal")
  @Column(name = "tag", length = MAX_TAG_LENGTH, nullable = false)
  @BatchSize(size = 50)
  private List<String> tags = new ArrayList<>();

  @Column(name = "preview_token", length = 64)
  private String previewToken;

  @Column(name = "series_id")
  private Long seriesId;

  @Column(name = "series_order")
  private Integer seriesOrder;

  @Column(name = "pin_order")
  private Integer pinOrder;

  // 본문의 글자·숫자 수. 쓸 때 재 두어 발견 피드가 본문을 읽지 않고 거른다.
  @Column(name = "body_text_length", nullable = false)
  private int bodyTextLength = 0;

  // markEdited()로만 오른다. 비교와 증가가 원자적이려면 findByIdForUpdate로 잠근 엔티티여야 한다.
  @Column(name = "content_version", nullable = false)
  private long contentVersion = 0L;

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

  public boolean isTakenDown() {
    return takenDownAt != null;
  }

  public void takeDown(Instant at) {
    if (takenDownAt != null) {
      return;
    }
    if (status == PostStatus.PUBLISHED) {
      this.status = PostStatus.UNPUBLISHED;
    } else if (status == PostStatus.SCHEDULED) {
      this.status = PostStatus.DRAFT;
      this.scheduledAt = null;
    }
    this.takenDownAt = at;
  }

  public void releaseTakeDown() {
    this.takenDownAt = null;
  }

  private void requireNotTakenDown() {
    if (takenDownAt != null) {
      throw new PostException(PostErrorCode.POST_TAKEN_DOWN);
    }
  }

  public void updateTitle(String title) {
    this.title = title;
  }

  // Slug is frozen once the post has ever been public (published or unpublished).
  // 한 번이라도 공개된 글의 주소는 공유된 링크라 바꾸지 않는다.
  public void numberSlug(long number) {
    if (publishedAt != null) {
      throw new PostException(PostErrorCode.SLUG_FROZEN, this.slug);
    }
    this.slug = String.valueOf(number);
  }

  public void updateSlug(String slug) {
    if (status == PostStatus.PUBLISHED || status == PostStatus.UNPUBLISHED) {
      throw new PostException(PostErrorCode.SLUG_FROZEN, this.slug);
    }
    this.slug = slug;
  }

  public void updateExcerpt(String excerpt) {
    this.excerpt = excerpt;
  }

  public void updateOgImage(String url, String key, boolean chosen) {
    this.ogImageUrl = url;
    this.ogImageKey = key;
    this.coverChosen = chosen;
  }

  public void clearOgImage() {
    this.ogImageUrl = null;
    this.ogImageKey = null;
    this.coverChosen = false;
  }

  public String thumbnailUrl() {
    return coverChosen ? ogImageUrl : null;
  }

  public void updateLanguageTag(String languageTag) {
    this.languageTag = languageTag;
  }

  public void markEdited() {
    this.lastEditedAt = Instant.now();
    this.contentVersion++;
  }

  public void requireContentVersion(long baseVersion) {
    if (baseVersion != contentVersion) {
      throw new PostException(PostErrorCode.POST_EDIT_CONFLICT, contentVersion)
          .with("contentVersion", contentVersion);
    }
  }

  public void measureBody(String bodyText) {
    this.bodyTextLength = DiscoveryQuality.meaningfulLength(bodyText);
  }

  public boolean isDiscoverable() {
    return bodyTextLength >= DiscoveryQuality.MIN_BODY_TEXT_LENGTH;
  }

  private void requireTitleToGoPublic() {
    if (title == null || title.isBlank()) {
      throw new PostException(PostErrorCode.TITLE_REQUIRED);
    }
  }

  public void publish() {
    requireNotTakenDown();
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
    requireNotTakenDown();
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

  public void republish() {
    requireNotTakenDown();
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
