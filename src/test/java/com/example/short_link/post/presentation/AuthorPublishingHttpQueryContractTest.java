package com.example.short_link.post.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

/** Author writes, previews, schedules, publishes, groups, measures, restores and removes a post. */
class AuthorPublishingHttpQueryContractTest extends ContentHttpJourneySupport {

  @Test
  void authorPublishesAndMaintainsTheirWritingWhileReadersSeeOnlyPublicState() throws Exception {
    long first = createPost("author-draft-create", "first-story", "First story");
    prepareAndPreviewDraft(first);
    scheduleAndPublish(first);
    long second = createPost("author-second-draft-create", "second-story", "Second story");
    publish("author-second-publish", second);
    long series = organizeSeries(first, second);
    readAndMeasure(first, series);
    reviseAndRestore(first);
    removeSeriesAndPosts(series, first, second);
    verifyContracts();
  }

  private void prepareAndPreviewDraft(long postId) throws Exception {
    assertThat(get("author-drafts-list", "/api/v1/posts", author).get(0).path("id").asLong())
        .isEqualTo(postId);
    assertThat(get("author-draft-detail", postPath(postId), author).path("status").asText())
        .isEqualTo("DRAFT");
    step("author-draft-other-reader-denied", "GET", postPath(postId), reader, null, 403);
    step("author-draft-public-hidden", "GET", publicPostPath("first-story"), null, null, 404);
    step(
        "author-draft-metadata-edit",
        "PATCH",
        postPath(postId),
        author,
        Map.of(
            "title",
            "Readable architecture",
            "excerpt",
            "A practical story",
            "tags",
            List.of("Java", "Architecture")),
        200);
    assertThat(
            jdbc.queryForList(
                "SELECT tag FROM post_tag WHERE post_id = ? ORDER BY ordinal",
                String.class,
                postId))
        .containsExactly("Java", "Architecture");
    step(
        "author-draft-markdown-write",
        "PUT",
        postPath(postId) + "/markdown",
        author,
        Map.of(
            "markdown",
            "# Clear responsibilities\n\nReadable architecture starts with explicit rules."),
        200);
    assertThat(
            get("author-draft-markdown-read", postPath(postId) + "/markdown", author)
                .path("markdown")
                .asText())
        .contains("Clear responsibilities", "explicit rules");
    assertThat(count("post_block", "post_id = ?", postId)).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT search_text FROM post_search_text WHERE post_id = ?", String.class, postId))
        .contains("Readable architecture", "explicit rules");
    String token =
        step(
                "author-preview-token-issue",
                "POST",
                postPath(postId) + "/preview-token",
                author,
                null,
                200)
            .path("token")
            .asText();
    assertThat(token).isNotBlank();
    assertThat(
            get("reader-draft-shared-preview", "/api/v1/public/preview/" + token, null)
                .path("post")
                .path("id")
                .asLong())
        .isEqualTo(postId);
    step(
        "reader-preview-unknown-token",
        "GET",
        "/api/v1/public/preview/unknown-share-token",
        null,
        null,
        404);
  }

  private void scheduleAndPublish(long postId) throws Exception {
    assertThat(
            step(
                    "author-post-schedule",
                    "POST",
                    postPath(postId) + "/schedule",
                    author,
                    Map.of("scheduledAt", Instant.now().plusSeconds(3600).toString()),
                    200)
                .path("status")
                .asText())
        .isEqualTo("SCHEDULED");
    assertThat(
            jdbc.queryForObject(
                "SELECT scheduled_at IS NOT NULL FROM posts WHERE id = ?", Boolean.class, postId))
        .isTrue();
    step("reader-scheduled-post-hidden", "GET", publicPostPath("first-story"), null, null, 404);
    assertThat(
            step(
                    "author-schedule-cancel",
                    "POST",
                    postPath(postId) + "/back-to-draft",
                    author,
                    null,
                    200)
                .path("status")
                .asText())
        .isEqualTo("DRAFT");
    publish("author-first-publish", postId);
    assertThat(count("post_revision", "post_id = ?", postId)).isEqualTo(1);
    step(
        "author-published-slug-frozen",
        "PATCH",
        postPath(postId),
        author,
        Map.of("slug", "changed-slug"),
        409);
    assertThat(jdbc.queryForObject("SELECT slug FROM posts WHERE id = ?", String.class, postId))
        .isEqualTo("first-story");
    step(
        "author-post-pin",
        "PUT",
        "/api/v1/posts/pins",
        author,
        Map.of("postIds", List.of(postId)),
        204);
    assertThat(
            jdbc.queryForObject("SELECT pin_order FROM posts WHERE id = ?", Integer.class, postId))
        .isZero();
  }

  private long organizeSeries(long first, long second) throws Exception {
    long series =
        step(
                "author-series-create",
                "POST",
                "/api/v1/series",
                author,
                Map.of("slug", "engineering", "title", "Engineering"),
                201)
            .path("series")
            .path("id")
            .asLong();
    assertThat(series).isPositive();
    step(
        "author-series-edit",
        "PATCH",
        "/api/v1/series/" + series,
        author,
        Map.of("title", "Readable engineering"),
        200);
    step(
        "author-series-order-posts",
        "PUT",
        "/api/v1/series/" + series + "/posts",
        author,
        Map.of("postIds", List.of(second, first)),
        200);
    assertThat(
            jdbc.queryForList(
                "SELECT id FROM posts WHERE series_id = ? ORDER BY series_order",
                Long.class,
                series))
        .containsExactly(second, first);
    assertThat(get("author-series-list", "/api/v1/series", author).get(0).path("id").asLong())
        .isEqualTo(series);
    assertThat(get("author-series-detail", "/api/v1/series/" + series, author).path("posts").size())
        .isEqualTo(2);
    step(
        "reader-private-series-edit-denied",
        "PATCH",
        "/api/v1/series/" + series,
        reader,
        Map.of("title", "Stolen"),
        403);
    assertThat(
            get(
                    "reader-public-series-list",
                    "/api/v1/public/profiles/" + author.username() + "/series",
                    null)
                .toString())
        .contains("Readable engineering");
    assertThat(
            get(
                    "reader-public-series-detail",
                    "/api/v1/public/profiles/" + author.username() + "/series/engineering",
                    null)
                .toString())
        .contains("Second story", "Readable architecture");
    assertThat(get("reader-series-discovery", "/api/v1/public/series", null).toString())
        .contains("Readable engineering");
    step(
        "reader-series-subscribe",
        "PUT",
        "/api/v1/series/" + series + "/subscription",
        reader,
        null,
        200);
    assertThat(count("series_subscription", "series_id = ? AND user_id = ?", series, reader.id()))
        .isEqualTo(1);
    assertThat(
            get(
                    "reader-series-subscription-status",
                    "/api/v1/series/" + series + "/subscription",
                    reader)
                .toString())
        .contains("true");
    assertThat(
            get("reader-series-subscription-ids", "/api/v1/users/me/series-subscriptions", reader)
                .get(0)
                .asLong())
        .isEqualTo(series);
    assertThat(
            get("reader-subscribed-series-cards", "/api/v1/users/me/subscribed-series", reader)
                .toString())
        .contains("Readable engineering");
    return series;
  }

  private void readAndMeasure(long first, long series) throws Exception {
    assertThat(
            get(
                    "reader-author-published-posts",
                    "/api/v1/public/profiles/" + author.username() + "/posts",
                    null)
                .path("posts")
                .get(0)
                .path("id")
                .asLong())
        .isEqualTo(first);
    assertThat(
            get("reader-public-post-series-navigation", publicPostPath("first-story"), null)
                .path("series")
                .path("total")
                .asInt())
        .isEqualTo(2);
    assertThat(
            rawStep(
                "reader-public-markdown-download",
                "GET",
                publicPostPath("first-story") + "/markdown",
                null,
                null,
                200))
        .contains("Clear responsibilities", "explicit rules");
    step(
        "reader-post-view-beacon",
        "POST",
        publicPostPath("first-story") + "/view?utm_source=journey&utm_campaign=readability",
        null,
        null,
        202);
    assertThat(count("post_view_event", "post_id = ? AND utm_source = ?", first, "journey"))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT view_count FROM posts WHERE id = ?", Long.class, first))
        .isEqualTo(1);
    assertThat(
            get("author-analytics-overview", "/api/v1/posts/analytics/overview", author)
                .path("lifetimeViews")
                .asLong())
        .isEqualTo(1);
    assertThat(
            get("author-analytics-post-table", "/api/v1/posts/analytics/posts", author).toString())
        .contains("Readable architecture");
    assertThat(
            get("author-post-analytics-detail", postPath(first) + "/analytics", author)
                .path("lifetimeViews")
                .asLong())
        .isEqualTo(1);
    assertThat(
            get("author-post-reader-stats", postPath(first) + "/stats", author)
                .path("totalVisits")
                .asLong())
        .isEqualTo(1);
    assertThat(
            get("author-series-reader-stats", "/api/v1/series/" + series + "/stats", author)
                .path("totalVisits")
                .asLong())
        .isEqualTo(1);
    assertThat(
            get("author-series-analytics-list", "/api/v1/posts/analytics/series", author)
                .toString())
        .contains("Readable engineering");
    assertThat(
            get(
                    "author-series-analytics-detail",
                    "/api/v1/posts/analytics/series/" + series,
                    author)
                .toString())
        .contains("Readable engineering");
    step(
        "reader-author-analytics-denied", "GET", postPath(first) + "/analytics", reader, null, 403);
    byte[] archive = download("author-posts-export-zip", "/api/v1/posts/export", author);
    Map<String, String> exported = new HashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        exported.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    assertThat(exported).containsKeys("first-story.md", "second-story.md");
    assertThat(exported.get("first-story.md")).contains("Readable architecture", "explicit rules");
  }

  private void reviseAndRestore(long postId) throws Exception {
    assertThat(
            get("author-revision-list", postPath(postId) + "/revisions", author)
                .get(0)
                .path("versionNumber")
                .asInt())
        .isEqualTo(1);
    step("author-post-unpublish", "POST", postPath(postId) + "/unpublish", author, null, 200);
    step("reader-unpublished-post-gone", "GET", publicPostPath("first-story"), null, null, 410);
    step(
        "author-unpublished-body-edit",
        "PUT",
        postPath(postId) + "/markdown",
        author,
        Map.of("markdown", "A replacement body before republication."),
        200);
    step("author-post-republish", "POST", postPath(postId) + "/republish", author, null, 200);
    assertThat(count("post_revision", "post_id = ?", postId)).isEqualTo(2);
    step(
        "author-revision-restore-first",
        "POST",
        postPath(postId) + "/revisions/1/restore",
        author,
        null,
        200);
    assertThat(
            jdbc.queryForObject(
                "SELECT search_text FROM post_search_text WHERE post_id = ?", String.class, postId))
        .contains("explicit rules")
        .doesNotContain("replacement body");
    assertThat(
            get("reader-restored-public-body", publicPostPath("first-story"), null)
                .path("blocks")
                .size())
        .isEqualTo(2);
  }

  private void removeSeriesAndPosts(long series, long first, long second) throws Exception {
    step(
        "reader-series-unsubscribe",
        "DELETE",
        "/api/v1/series/" + series + "/subscription",
        reader,
        null,
        200);
    assertThat(count("series_subscription", "series_id = ?", series)).isZero();
    step("author-series-delete", "DELETE", "/api/v1/series/" + series, author, null, 204);
    assertThat(count("series", "id = ?", series)).isZero();
    assertThat(count("posts", "series_id = ?", series)).isZero();
    step("author-post-delete", "DELETE", postPath(first), author, null, 204);
    assertThat(count("posts", "id = ?", first)).isZero();
    assertThat(count("post_block", "post_id = ?", first)).isZero();
    assertThat(count("post_revision", "post_id = ?", first)).isZero();
    assertThat(count("post_search_text", "post_id = ?", first)).isZero();
    step(
        "reader-deleted-public-post-missing",
        "GET",
        publicPostPath("first-story"),
        null,
        null,
        404);
    step("author-second-post-delete", "DELETE", postPath(second), author, null, 204);
  }
}
