package com.example.short_link.admin.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.observability.RequestMetricsRecorder;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.application.ClickFlusher;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.testsupport.OperationalHttpJourneySupport;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminOperationsHttpQueryContractTest extends OperationalHttpJourneySupport {

  @Autowired private LinkRepository links;
  @Autowired private PostRepository posts;
  @Autowired private RequestMetricsRecorder requestMetrics;
  @Autowired private ClickFlusher clickFlusher;

  @Test
  void investigatesAnAccountAndItsLinkAndLeavesARealWarning() throws Exception {
    Actor admin = actor("ops-admin", true);
    Actor member = actor("ops-member", false);
    String code = "opsreview";
    transactions.executeWithoutResult(
        transaction ->
            links.save(
                new LinkEntity(
                    "https://example.com/review", new ShortCode(code), member.id(), null)));
    assertThat(count("link", "user_id = ?", member.id())).isEqualTo(1);

    step("ops-denies-member", "GET", "/api/v1/admin/users", member, null, 403);
    var token = step("ops-mint-token", "POST", "/api/v1/admin/access-token", admin, null, 200);
    assertThat(token.path("accessToken").asText()).isNotBlank();
    assertThat(token.path("expiresInSeconds").asLong()).isPositive();
    assertThat(
            step(
                    "ops-search-users",
                    "GET",
                    "/api/v1/admin/users?q=" + member.username(),
                    admin,
                    null,
                    200)
                .toString())
        .contains(member.username());
    assertThat(
            step("ops-user-detail", "GET", "/api/v1/admin/users/" + member.id(), admin, null, 200)
                .toString())
        .contains(member.username());
    assertThat(
            step(
                    "ops-search-links",
                    "GET",
                    "/api/v1/admin/links?ownerId=" + member.id(),
                    admin,
                    null,
                    200)
                .toString())
        .contains(code);
    assertThat(
            step("ops-link-detail", "GET", "/api/v1/admin/links/" + code, admin, null, 200)
                .toString())
        .contains(code);
    assertThat(
            step("ops-link-activity", "GET", "/api/v1/admin/links/activity", admin, null, 200)
                .toString())
        .contains(code);
    step(
        "ops-warn-user",
        "POST",
        "/api/v1/admin/users/" + member.id() + "/warning",
        admin,
        Map.of("message", "Please review this link", "shortCode", code),
        204);
    assertThat(count("link_notification", "recipient_user_id = ?", member.id())).isEqualTo(1);
    assertThat(
            step(
                    "ops-repair-webhook-formats",
                    "POST",
                    "/api/v1/admin/webhooks/redetect-formats",
                    admin,
                    null,
                    200)
                .isObject())
        .isTrue();
  }

  @Test
  void readsProductAndRuntimeMetricsWithAuthenticatedRequests() throws Exception {
    requestMetrics.flush();
    Actor admin = actor("metrics-admin", true);
    Actor member = actor("metrics-member", false);
    transactions.executeWithoutResult(
        transaction ->
            links.save(
                new LinkEntity(
                    "https://example.com/metrics",
                    new ShortCode("opsmetrics"),
                    member.id(),
                    null)));
    step("ops-metric-redirect", "GET", "/opsmetrics", null, null, 302);
    deliverBackgroundWork(
        "ops-metric-worker-persist",
        () -> {
          clickFlusher.flush();
          requestMetrics.flush();
        });
    assertThat(count("request_metrics", "short_code = ? AND status = 302", "opsmetrics"))
        .isEqualTo(1);
    assertThat(
            count(
                "click_event",
                "link_id = (SELECT id FROM link WHERE short_code = ?)",
                "opsmetrics"))
        .isEqualTo(1);
    var overview = step("ops-overview", "GET", "/api/v1/admin/overview", admin, null, 200);
    assertThat(overview.path("totals").path("users").asLong()).isEqualTo(count("users", "1 = 1"));
    assertThat(overview.path("totals").path("links").asLong()).isEqualTo(count("link", "1 = 1"));
    assertThat(overview.path("totals").path("clicks").asLong())
        .isEqualTo(count("click_event", "1 = 1"));
    for (String endpoint : List.of("top-users-by-links", "top-users-by-clicks")) {
      var response = step("ops-" + endpoint, "GET", "/api/v1/admin/" + endpoint, admin, null, 200);
      var items = response.path("items");
      assertThat(items.isArray()).isTrue();
      assertThat(
              IntStream.range(0, items.size())
                  .mapToObj(items::get)
                  .filter(item -> item.path("userId").asLong() == member.id())
                  .toList())
          .as("%s includes the fixture owner's aggregated result", endpoint)
          .singleElement()
          .satisfies(
              item -> {
                assertThat(item.path("email").asText())
                    .isEqualTo(member.username() + "@example.com");
                assertThat(item.path("count").asLong()).isEqualTo(1);
              });
    }
    var topLinks =
        step(
            "ops-top-links-by-clicks",
            "GET",
            "/api/v1/admin/top-links-by-clicks",
            admin,
            null,
            200);
    var linkItems = topLinks.path("items");
    assertThat(linkItems.isArray()).isTrue();
    assertThat(
            IntStream.range(0, linkItems.size())
                .mapToObj(linkItems::get)
                .filter(item -> item.path("shortCode").asText().equals("opsmetrics"))
                .toList())
        .as("top links includes the redirected fixture link")
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.path("ownerEmail").asText())
                  .isEqualTo(member.username() + "@example.com");
              assertThat(item.path("clickCount").asLong()).isEqualTo(1);
            });
    for (String endpoint :
        List.of("cohort", "lifecycle", "active-users", "blog/metrics", "funnel")) {
      var response =
          step(
              "ops-" + endpoint.replace('/', '-'),
              "GET",
              "/api/v1/admin/" + endpoint,
              admin,
              null,
              200);
      assertThat(response.isObject() || response.isArray()).isTrue();
    }
    for (String endpoint :
        List.of(
            "health-metrics",
            "route-metrics",
            "link-metrics",
            "recent-errors",
            "metrics/routes",
            "metrics/system",
            "metrics/requests")) {
      var response =
          step(
              "ops-" + endpoint.replace('/', '-'),
              "GET",
              "/api/v1/admin/" + endpoint,
              admin,
              null,
              200);
      assertThat(response.isObject() || response.isArray()).isTrue();
    }
    assertThat(
            step(
                    "ops-metrics-outcomes",
                    "GET",
                    "/api/v1/admin/metrics/outcomes?shortCode=opsmetrics",
                    admin,
                    null,
                    200)
                .toString())
        .contains("opsmetrics");
    assertThat(count("users", "id = ?", member.id())).isEqualTo(1);
  }

  @Test
  void handlesAnAbuseReportAndModeratesThePersistedPost() throws Exception {
    Actor admin = actor("moderator", true);
    Actor author = actor("reported-author", false);
    Actor reader = actor("reporter", false);
    long postId =
        transactions.execute(
            transaction -> {
              PostEntity post =
                  new PostEntity(author.id(), "reported-post", "Reported title", "ko");
              post.publish();
              return posts.save(post).getId();
            });
    step(
        "ops-submit-abuse",
        "POST",
        "/api/v1/public/abuse-reports",
        reader,
        Map.of(
            "subjectType",
            "POST",
            "subjectId",
            postId,
            "reasonCode",
            "SPAM",
            "detail",
            "Reported from the reader journey"),
        202);
    long reportId =
        jdbc.queryForObject(
            "SELECT id FROM abuse_report WHERE subject_id = ? AND subject_type = 'POST'",
            Long.class,
            postId);
    assertThat(
            step("ops-abuse-backlog", "GET", "/api/v1/admin/abuse-reports", admin, null, 200)
                .toString())
        .contains("Reported title");
    step(
        "ops-resolve-abuse",
        "POST",
        "/api/v1/admin/abuse-reports/" + reportId + "/resolve",
        admin,
        Map.of("resolution", "RESOLVED", "action", "NONE", "adminNote", "Reviewed by an operator"),
        200);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM abuse_report WHERE id = ?", String.class, reportId))
        .isEqualTo("RESOLVED");
    step(
        "ops-denies-author-moderation",
        "PATCH",
        "/api/v1/admin/posts/" + postId,
        author,
        Map.of("title", "Forbidden edit"),
        403);
    step(
        "ops-edit-post",
        "PATCH",
        "/api/v1/admin/posts/" + postId,
        admin,
        Map.of("title", "Reviewed title", "tags", List.of("reviewed")),
        200);
    assertThat(jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, postId))
        .isEqualTo("Reviewed title");
    step(
        "ops-unpublish-post",
        "POST",
        "/api/v1/admin/posts/" + postId + "/unpublish",
        admin,
        null,
        204);
    assertThat(jdbc.queryForObject("SELECT status FROM posts WHERE id = ?", String.class, postId))
        .isEqualTo("UNPUBLISHED");
    step("ops-delete-post", "DELETE", "/api/v1/admin/posts/" + postId, admin, null, 204);
    assertThat(count("posts", "id = ?", postId)).isZero();
  }

  @Test
  void blocksAndUnblocksADestinationDomain() throws Exception {
    Actor admin = actor("domain-moderator", true);
    step(
        "ops-block-domain",
        "POST",
        "/api/v1/admin/blocked-domains",
        admin,
        Map.of("domain", "blocked.example.com", "reason", "Abuse investigation"),
        201);
    assertThat(count("blocked_domain", "domain = ?", "blocked.example.com")).isEqualTo(1);
    assertThat(
            step(
                    "ops-list-blocked-domains",
                    "GET",
                    "/api/v1/admin/blocked-domains",
                    admin,
                    null,
                    200)
                .toString())
        .contains("blocked.example.com");
    step(
        "ops-unblock-domain",
        "DELETE",
        "/api/v1/admin/blocked-domains/blocked.example.com",
        admin,
        null,
        204);
    assertThat(count("blocked_domain", "domain = ?", "blocked.example.com")).isZero();
  }
}
