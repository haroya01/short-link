package com.example.short_link.post.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class PostEntityTest {

  private PostEntity newPost() {
    return new PostEntity(1L, "first-post", "First Post", "ko");
  }

  @Test
  void constructorSetsDefaults() {
    PostEntity p = newPost();
    assertThat(p.getUserId()).isEqualTo(1L);
    assertThat(p.getSlug()).isEqualTo("first-post");
    assertThat(p.getTitle()).isEqualTo("First Post");
    assertThat(p.getLanguageTag()).isEqualTo("ko");
    assertThat(p.getStatus()).isEqualTo(PostStatus.DRAFT);
    assertThat(p.isDraft()).isTrue();
    assertThat(p.isPublished()).isFalse();
    assertThat(p.getViewCount()).isZero();
    assertThat(p.getPublishedAt()).isNull();
    assertThat(p.getScheduledAt()).isNull();
  }

  @Test
  void publishRequiresATitle() {
    PostEntity untitled = new PostEntity(1L, "draft-x", "", "ko");
    assertThatThrownBy(untitled::publish)
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.TITLE_REQUIRED);
  }

  @Test
  void scheduleRequiresATitle() {
    PostEntity untitled = new PostEntity(1L, "draft-y", "  ", "ko");
    assertThatThrownBy(() -> untitled.schedule(Instant.now().plus(1, ChronoUnit.DAYS)))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.TITLE_REQUIRED);
  }

  @Test
  void publishSucceedsWithATitle() {
    PostEntity p = newPost();
    p.publish();
    assertThat(p.isPublished()).isTrue();
  }

  @Test
  void isOwnedByMatches() {
    PostEntity p = newPost();
    assertThat(p.isOwnedBy(1L)).isTrue();
    assertThat(p.isOwnedBy(2L)).isFalse();
  }

  @Test
  void publishMovesToPublishedAndStampsTimestamp() {
    PostEntity p = newPost();
    Instant before = Instant.now();
    p.publish();
    assertThat(p.isPublished()).isTrue();
    assertThat(p.getPublishedAt()).isNotNull();
    assertThat(p.getPublishedAt()).isAfterOrEqualTo(before);
  }

  @Test
  void publishIsIdempotent() {
    PostEntity p = newPost();
    p.publish();
    Instant firstPublishAt = p.getPublishedAt();
    p.publish();
    assertThat(p.getPublishedAt()).isEqualTo(firstPublishAt);
  }

  @Test
  void scheduleSetsStatusAndTimestamp() {
    PostEntity p = newPost();
    Instant when = Instant.now().plus(1, ChronoUnit.HOURS);
    p.schedule(when);
    assertThat(p.isScheduled()).isTrue();
    assertThat(p.getScheduledAt()).isEqualTo(when);
  }

  @Test
  void scheduleInPastRejected() {
    PostEntity p = newPost();
    Instant past = Instant.now().minus(1, ChronoUnit.MINUTES);
    assertThatThrownBy(() -> p.schedule(past))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.SCHEDULE_IN_PAST);
  }

  @Test
  void scheduleAfterPublishRejected() {
    PostEntity p = newPost();
    p.publish();
    Instant when = Instant.now().plus(1, ChronoUnit.HOURS);
    assertThatThrownBy(() -> p.schedule(when))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.SCHEDULE_AFTER_PUBLISH);
  }

  @Test
  void publishFromScheduleClearsScheduledAt() {
    PostEntity p = newPost();
    p.schedule(Instant.now().plus(1, ChronoUnit.HOURS));
    p.publish();
    assertThat(p.isPublished()).isTrue();
    assertThat(p.getScheduledAt()).isNull();
  }

  @Test
  void unpublishOnlyAfterPublished() {
    PostEntity p = newPost();
    assertThatThrownBy(p::unpublish)
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.UNPUBLISH_NOT_PUBLISHED);
  }

  @Test
  void unpublishPreservesPublishedAt() {
    PostEntity p = newPost();
    p.publish();
    Instant publishedAt = p.getPublishedAt();
    p.unpublish();
    assertThat(p.isUnpublished()).isTrue();
    assertThat(p.getPublishedAt()).isEqualTo(publishedAt);
  }

  @Test
  void republishRestoresVisibility() {
    PostEntity p = newPost();
    p.publish();
    p.unpublish();
    p.republish();
    assertThat(p.isPublished()).isTrue();
  }

  @Test
  void republishOnlyFromUnpublished() {
    PostEntity p = newPost();
    assertThatThrownBy(p::republish)
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.REPUBLISH_NOT_UNPUBLISHED);
  }

  @Test
  void backToDraftFromScheduledClearsScheduledAt() {
    PostEntity p = newPost();
    p.schedule(Instant.now().plus(1, ChronoUnit.HOURS));
    p.backToDraft();
    assertThat(p.isDraft()).isTrue();
    assertThat(p.getScheduledAt()).isNull();
  }

  @Test
  void backToDraftRejectedFromDraft() {
    PostEntity p = newPost();
    assertThatThrownBy(p::backToDraft)
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.BACK_TO_DRAFT_NOT_SCHEDULED);
  }

  @Test
  void slugUpdatableInDraft() {
    PostEntity p = newPost();
    p.updateSlug("new-slug");
    assertThat(p.getSlug()).isEqualTo("new-slug");
  }

  @Test
  void slugUpdatableInScheduled() {
    PostEntity p = newPost();
    p.schedule(Instant.now().plus(1, ChronoUnit.HOURS));
    p.updateSlug("renamed");
    assertThat(p.getSlug()).isEqualTo("renamed");
  }

  @Test
  void slugFrozenAfterPublish() {
    PostEntity p = newPost();
    p.publish();
    assertThatThrownBy(() -> p.updateSlug("attempted-rename"))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.SLUG_FROZEN);
  }

  @Test
  void slugFrozenAfterUnpublish() {
    PostEntity p = newPost();
    p.publish();
    p.unpublish();
    assertThatThrownBy(() -> p.updateSlug("attempted-rename"))
        .isInstanceOf(PostException.class)
        .extracting(e -> ((PostException) e).errorCode())
        .isEqualTo(PostErrorCode.SLUG_FROZEN);
  }

  @Test
  void incrementViewCount() {
    PostEntity p = newPost();
    p.incrementViewCount();
    p.incrementViewCount();
    p.incrementViewCount();
    assertThat(p.getViewCount()).isEqualTo(3L);
  }

  @Test
  void updateTitleAndExcerpt() {
    PostEntity p = newPost();
    p.updateTitle("New Title");
    p.updateExcerpt("Short summary.");
    assertThat(p.getTitle()).isEqualTo("New Title");
    assertThat(p.getExcerpt()).isEqualTo("Short summary.");
  }

  @Test
  void updateAndClearOgImage() {
    PostEntity p = newPost();
    p.updateOgImage("https://cdn/og/1.png", "og/1.png");
    assertThat(p.getOgImageUrl()).isEqualTo("https://cdn/og/1.png");
    assertThat(p.getOgImageKey()).isEqualTo("og/1.png");
    p.clearOgImage();
    assertThat(p.getOgImageUrl()).isNull();
    assertThat(p.getOgImageKey()).isNull();
  }

  @Test
  void updateLanguageTag() {
    PostEntity p = newPost();
    p.updateLanguageTag("ja");
    assertThat(p.getLanguageTag()).isEqualTo("ja");
  }

  @Test
  void updateTagsTrimsDedupesAndDropsBlanks() {
    PostEntity p = newPost();
    p.updateTags(java.util.List.of("  Spring ", "spring", "", "  ", "JPA"));
    assertThat(p.getTags()).containsExactly("Spring", "JPA");
  }

  @Test
  void updateTagsTruncatesToMaxLength() {
    PostEntity p = newPost();
    String tooLong = "x".repeat(PostEntity.MAX_TAG_LENGTH + 10);
    p.updateTags(java.util.List.of(tooLong));
    assertThat(p.getTags()).hasSize(1);
    assertThat(p.getTags().get(0)).hasSize(PostEntity.MAX_TAG_LENGTH);
  }

  @Test
  void updateTagsCapsCount() {
    PostEntity p = newPost();
    java.util.List<String> many =
        java.util.stream.IntStream.range(0, PostEntity.MAX_TAGS + 15)
            .mapToObj(i -> "tag" + i)
            .toList();
    p.updateTags(many);
    assertThat(p.getTags()).hasSize(PostEntity.MAX_TAGS);
  }

  @Test
  void updateTagsWithNullClears() {
    PostEntity p = newPost();
    p.updateTags(java.util.List.of("a", "b"));
    p.updateTags(null);
    assertThat(p.getTags()).isEmpty();
  }
}
