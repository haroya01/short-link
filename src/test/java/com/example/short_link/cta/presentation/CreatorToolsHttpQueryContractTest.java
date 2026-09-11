package com.example.short_link.cta.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.testsupport.OperationalHttpJourneySupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CreatorToolsHttpQueryContractTest extends OperationalHttpJourneySupport {

  @Test
  void createsEditsAndDeletesAnOwnedCallToAction() throws Exception {
    Actor owner = actor("cta-owner", false);
    Actor other = actor("cta-other", false);
    long id =
        step(
                "creator-cta-create",
                "POST",
                "/api/v1/ctas",
                owner,
                Map.of("label", "Read my work", "url", "https://example.com/work"),
                201)
            .path("id")
            .asLong();
    assertThat(count("cta", "id = ? AND user_id = ?", id, owner.id())).isEqualTo(1);
    assertThat(step("creator-cta-list", "GET", "/api/v1/ctas", owner, null, 200).toString())
        .contains("Read my work");
    assertThat(
            step("creator-cta-detail", "GET", "/api/v1/ctas/" + id, owner, null, 200)
                .path("id")
                .asLong())
        .isEqualTo(id);
    step(
        "creator-cta-denies-other",
        "PATCH",
        "/api/v1/ctas/" + id,
        other,
        Map.of("label", "Not mine"),
        403);
    step(
        "creator-cta-update",
        "PATCH",
        "/api/v1/ctas/" + id,
        owner,
        Map.of("label", "Updated call to action"),
        200);
    assertThat(jdbc.queryForObject("SELECT label FROM cta WHERE id = ?", String.class, id))
        .isEqualTo("Updated call to action");
    step("creator-cta-delete", "DELETE", "/api/v1/ctas/" + id, owner, null, 204);
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM cta WHERE id = ?", Boolean.class, id))
        .isTrue();
    assertThat(
            step("creator-cta-deleted-detail", "GET", "/api/v1/ctas/" + id, owner, null, 200)
                .path("deleted")
                .asBoolean())
        .isTrue();
    assertThat(
            step("creator-cta-active-list-after-delete", "GET", "/api/v1/ctas", owner, null, 200)
                .isEmpty())
        .isTrue();
  }

  @Test
  void managesABlogWebhookWithoutContactingItsReceiver() throws Exception {
    Actor owner = actor("hook-owner", false);
    Actor other = actor("hook-other", false);
    var created =
        step(
            "creator-blog-hook-create",
            "POST",
            "/api/v1/blog/webhooks",
            owner,
            Map.of(
                "url",
                "https://example.com/blog-hook",
                "name",
                "Reader events",
                "events",
                List.of("LIKE")),
            201);
    long id = created.path("id").asLong();
    assertThat(id).isPositive();
    assertThat(count("blog_webhook", "id = ? AND user_id = ?", id, owner.id())).isEqualTo(1);
    assertThat(
            step("creator-blog-hook-list", "GET", "/api/v1/blog/webhooks", owner, null, 200)
                .toString())
        .contains("Reader events");
    step(
        "creator-blog-hook-denies-other",
        "PATCH",
        "/api/v1/blog/webhooks/" + id,
        other,
        Map.of("enabled", false),
        404);
    step(
        "creator-blog-hook-update",
        "PATCH",
        "/api/v1/blog/webhooks/" + id,
        owner,
        Map.of("name", "Paused receiver", "enabled", false),
        200);
    assertThat(
            jdbc.queryForObject("SELECT enabled FROM blog_webhook WHERE id = ?", Boolean.class, id))
        .isFalse();
    step("creator-blog-hook-delete", "DELETE", "/api/v1/blog/webhooks/" + id, owner, null, 204);
    assertThat(count("blog_webhook", "id = ?", id)).isZero();
  }

  @Test
  void recordsReaderBehaviorAndDropsAnInvalidEvent() throws Exception {
    Map<String, Object> read =
        Map.of("name", "read_progress", "postId", 919191, "depthPct", 50, "dwellMs", 4000);
    step(
        "creator-reader-behavior",
        "POST",
        "/api/v1/public/behavior-events",
        null,
        Map.of(
            "sessionId",
            "journey-session",
            "events",
            List.of(read, Map.of("name", "not-an-event"))),
        202);
    assertThat(count("behavior_event", "session_id = ?", "journey-session")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT depth_pct FROM behavior_event WHERE session_id = ?",
                Integer.class,
                "journey-session"))
        .isEqualTo(50);
  }
}
