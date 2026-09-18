package com.example.short_link.link.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.short_link.link.stats.application.ClickFlusher;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.link.stats.domain.repository.ClickTimeReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClickBucketRow;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A link owner creates, organizes, protects, visits, measures, and deletes real persisted links.
 */
class LinkLifecycleHttpQueryContractTest extends LinkJourneyHttpSupport {
  @Autowired private ClickFlusher clickFlusher;
  @Autowired private ClickEventRepository clickEvents;
  @Autowired private ClickTimeReadRepository clickTime;
  @Autowired private ClickTotalsReadRepository clickTotals;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void ownerKeepsFavoritesInOrderAndOpensAccountOverview() throws Exception {
    String first = createLink("workspace-create-first");
    String second = createLink("workspace-create-second");
    request(
        "workspace-favorite-add-first",
        owner,
        "PUT",
        "/api/v1/links/me/favorites/" + first,
        null,
        204);
    request(
        "workspace-favorite-add-second",
        owner,
        "PUT",
        "/api/v1/links/me/favorites/" + second,
        null,
        204);
    request(
        "workspace-favorite-order",
        owner,
        "PUT",
        "/api/v1/links/me/favorites/order",
        Map.of("shortCodes", List.of(second, first)),
        204);
    var favorites =
        request("workspace-favorite-list", owner, "GET", "/api/v1/links/me/favorites", null, 200);
    assertThat(favorites.path("items").get(0).path("shortCode").asText()).isEqualTo(second);
    assertThat(favorites.path("items").size()).isEqualTo(2);
    var selected =
        request(
            "workspace-selected-links",
            owner,
            "GET",
            "/api/v1/links/me/by-codes?codes=" + first,
            null,
            200);
    assertThat(selected.path("items").get(0).path("shortCode").asText()).isEqualTo(first);
    var overview =
        request("workspace-overview", owner, "GET", "/api/v1/links/me/overview", null, 200);
    assertThat(overview.path("totalLinks").asInt()).isEqualTo(2);
    assertThat(overview.path("dailyClicks").size()).isEqualTo(7);
    request(
        "workspace-favorite-remove",
        owner,
        "DELETE",
        "/api/v1/links/me/favorites/" + first,
        null,
        204);
  }

  @Test
  void ownerManagesLinkMetadataTagsAndRoutingWithoutGivingAnotherUserWriteAccess()
      throws Exception {
    String code = createLink("link-create-owned");
    long linkId = linkId(code);
    String path = "/api/v1/links/" + code;

    assertThat(
            request("link-detail-owned", owner, "GET", path + "/detail", null, 200)
                .path("originalUrl")
                .asText())
        .isEqualTo(text("SELECT original_url FROM link WHERE id = ?", linkId));
    assertThat(request("link-list-owned", owner, "GET", "/api/v1/links/me", null, 200).toString())
        .contains(code);
    request(
        "link-update-foreign-denied", stranger, "PATCH", path, Map.of("note", "intrusion"), 403);
    assertThat(text("SELECT note FROM link WHERE id = ?", linkId)).isNull();
    request(
        "link-update-owned",
        owner,
        "PATCH",
        path,
        Map.of("note", "Launch link", "expiredMessage", "Launch ended"),
        200);
    assertThat(text("SELECT note FROM link WHERE id = ?", linkId)).isEqualTo("Launch link");

    long tagId =
        request(
                "tag-create",
                owner,
                "POST",
                "/api/v1/tags",
                Map.of("name", "launch", "color", "#123456"),
                201)
            .path("id")
            .asLong();
    request(
        "tag-rename",
        owner,
        "PATCH",
        "/api/v1/tags/" + tagId,
        Map.of("name", "release", "color", "#654321"),
        200);
    assertThat(text("SELECT name FROM tag WHERE id = ?", tagId)).isEqualTo("release");
    assertThat(request("tag-list", owner, "GET", "/api/v1/tags", null, 200).toString())
        .contains("release");
    request(
        "link-tags-replace", owner, "PUT", path + "/tags", Map.of("tags", List.of("release")), 200);
    assertThat(
            number("SELECT COUNT(*) FROM link_tag WHERE link_id = ? AND tag_id = ?", linkId, tagId))
        .isEqualTo(1);
    assertThat(request("link-tags-read", owner, "GET", path + "/tags", null, 200).toString())
        .contains("release");
    assertThat(
            request("link-list-filter-tag", owner, "GET", "/api/v1/links/me?tag=release", null, 200)
                .toString())
        .contains(code);

    long destination =
        request(
                "link-destination-add",
                owner,
                "POST",
                path + "/destinations",
                Map.of("url", "https://example.com/variant", "weight", 100, "label", "Variant"),
                201)
            .path("id")
            .asLong();
    request(
        "link-destination-update",
        owner,
        "PATCH",
        path + "/destinations/" + destination,
        Map.of("label", "Mobile", "deviceClass", "mobile", "enabled", true),
        200);
    assertThat(text("SELECT label FROM link_destination WHERE id = ?", destination))
        .isEqualTo("Mobile");
    assertThat(
            request("link-destination-list", owner, "GET", path + "/destinations", null, 200)
                .toString())
        .contains("Mobile");
    request(
        "link-countries-block",
        owner,
        "PUT",
        path + "/blocked-countries",
        Map.of("codes", "jp,kr"),
        200);
    assertThat(text("SELECT blocked_countries FROM link WHERE id = ?", linkId))
        .contains("JP", "KR");
    assertThat(
            request("link-countries-read", owner, "GET", path + "/blocked-countries", null, 200)
                .toString())
        .contains("JP", "KR");
    request(
        "link-og-override",
        owner,
        "PATCH",
        path + "/og",
        Map.of("ogTitle", "Launch preview", "ogDescription", "Campaign destination"),
        200);
    assertThat(text("SELECT og_title_override FROM link WHERE id = ?", linkId))
        .isEqualTo("Launch preview");

    request(
        "link-destination-delete",
        owner,
        "DELETE",
        path + "/destinations/" + destination,
        null,
        204);
    assertThat(number("SELECT COUNT(*) FROM link_destination WHERE id = ?", destination)).isZero();
    request("tag-delete", owner, "DELETE", "/api/v1/tags/" + tagId, null, 204);
    assertThat(number("SELECT COUNT(*) FROM link_tag WHERE link_id = ?", linkId)).isZero();
    request("link-delete-foreign-denied", stranger, "DELETE", path, null, 403);
    assertThat(number("SELECT COUNT(*) FROM link WHERE id = ?", linkId)).isEqualTo(1);
    request("link-delete-owned", owner, "DELETE", path, null, 204);
    assertThat(number("SELECT COUNT(*) FROM link WHERE id = ?", linkId)).isZero();
  }

  @Test
  void visitorUnlocksOnceAndOwnerReadsTheCommittedClickThroughStatisticsAndExports()
      throws Exception {
    String code = createLink("link-create-protected");
    long linkId = linkId(code);
    String path = "/api/v1/links/" + code;
    request(
        "link-protect",
        owner,
        "PATCH",
        path + "/protection",
        Map.of("password", "launch-password", "maxViews", 1),
        200);
    assertThat(text("SELECT password_hash FROM link WHERE id = ?", linkId))
        .isNotBlank()
        .isNotEqualTo("launch-password");
    assertThat(number("SELECT max_views FROM link WHERE id = ?", linkId)).isEqualTo(1);
    var prompt = raw("link-password-prompt", null, "GET", "/" + code, null, null, 200);
    assertThat(new String(prompt.body(), StandardCharsets.UTF_8)).contains("password");
    raw(
        "link-unlock-wrong",
        null,
        "POST",
        "/" + code,
        "password=wrong",
        "application/x-www-form-urlencoded",
        401);
    assertThat(number("SELECT view_count FROM link WHERE id = ?", linkId)).isZero();
    Instant visitStartedAt = Instant.now();
    var unlocked =
        raw(
            "link-unlock-correct",
            null,
            "POST",
            "/" + code,
            "password=launch-password",
            "application/x-www-form-urlencoded",
            200);
    Instant visitCompletedAt = Instant.now();
    assertThat(new String(unlocked.body(), StandardCharsets.UTF_8)).contains("example.com");
    assertThat(number("SELECT view_count FROM link WHERE id = ?", linkId)).isEqualTo(1);

    // The scheduler is disabled: execute its real delivery boundary, then read the committed click.
    var flushed =
        contracts.captureBackgroundDelivery(
            "link-click-worker-persist",
            () -> {
              clickFlusher.flush();
              awaitAsyncWrites();
              return "completed";
            });
    captures.add(flushed);
    assertThat(number("SELECT COUNT(*) FROM click_event WHERE link_id = ?", linkId)).isEqualTo(1);
    Instant storedVisitAt =
        Instant.ofEpochSecond(
            number("SELECT UNIX_TIMESTAMP(clicked_at) FROM click_event WHERE link_id = ?", linkId));
    // TIMESTAMP(0) keeps whole seconds. Check the physical DB epoch as well as the ORM round trip:
    // the same incorrect calendar on write/read can otherwise hide a nine-hour storage shift.
    assertThat(storedVisitAt)
        .isBetween(
            Instant.ofEpochSecond(visitStartedAt.getEpochSecond()),
            Instant.ofEpochSecond(visitCompletedAt.getEpochSecond()));
    assertThat(clickEvents.findEventsByLinkIdLatest(linkId, 1).getFirst().getClickedAt())
        .isEqualTo(storedVisitAt);
    assertThat(clickTotals.findFirstClickAt(linkId)).isEqualTo(storedVisitAt);
    // HQL comparison parameters must inherit the same field mapping as inserts and projections.
    assertThat(clickTotals.countSinceByLinkId(linkId, storedVisitAt)).isEqualTo(1);
    assertThat(clickTotals.countSinceByLinkId(linkId, storedVisitAt.plusSeconds(1))).isZero();

    request(
        "link-update-stat-tags",
        owner,
        "PUT",
        path + "/tags",
        Map.of("tags", List.of("measured", "release")),
        200);
    String updatedUrl = "https://example.com/updated-" + code;
    var updated =
        request(
            "link-update-with-statistics",
            owner,
            "PATCH",
            path,
            Map.of("originalUrl", updatedUrl, "note", "Statistics survive editing"),
            200);
    assertThat(updated.path("originalUrl").asText())
        .isEqualTo(text("SELECT original_url FROM link WHERE id = ?", linkId))
        .isEqualTo(updatedUrl);
    assertThat(updated.path("clickCount").asLong())
        .isEqualTo(number("SELECT COUNT(*) FROM click_event WHERE link_id = ?", linkId))
        .isEqualTo(1);
    List<String> storedTags =
        jdbc.queryForList(
            "SELECT t.name FROM tag t JOIN link_tag lt ON lt.tag_id = t.id WHERE lt.link_id = ? ORDER BY t.name",
            String.class,
            linkId);
    assertThat(storedTags).containsExactly("measured", "release");
    assertThat(updated.path("tags")).isEqualTo(json.valueToTree(storedTags));
    ZoneId ownerZone = ZoneId.of(updated.path("timezone").asText());
    LocalDate today = LocalDate.now(ownerZone);
    List<Long> storedDailyClicks = new ArrayList<>();
    for (int daysAgo = 6; daysAgo >= 0; daysAgo--) {
      Instant dayStart = today.minusDays(daysAgo).atStartOfDay(ownerZone).toInstant();
      storedDailyClicks.add(
          number(
              "SELECT COUNT(*) FROM click_event WHERE link_id = ? AND is_bot = false "
                  + "AND UNIX_TIMESTAMP(clicked_at) >= ? AND UNIX_TIMESTAMP(clicked_at) < ?",
              linkId,
              dayStart.getEpochSecond(),
              today.minusDays(daysAgo).plusDays(1).atStartOfDay(ownerZone).toEpochSecond()));
    }
    assertThat(storedDailyClicks).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 1L);
    List<Long> responseDailyClicks = new ArrayList<>();
    updated.path("clicksLast7d").forEach(value -> responseDailyClicks.add(value.asLong()));
    assertThat(responseDailyClicks).containsExactlyElementsOf(storedDailyClicks);
    assertThat(
            number(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'LINK_UPDATED' AND target_id = ?",
                code))
        .isEqualTo(1);
    assertThat(text("SELECT og_title FROM link_og_metadata WHERE link_id = ?", linkId))
        .isEqualTo("Fixture destination");
    assertThat(
            request("link-stats-private", owner, "GET", path + "/stats", null, 200)
                .path("totalClicks")
                .asLong())
        .isEqualTo(1);
    request("link-stats-foreign-denied", stranger, "GET", path + "/stats", null, 403);
    request("link-stats-public-denied", null, "GET", path + "/public-stats", null, 404);
    request(
        "link-stats-publish",
        owner,
        "PATCH",
        path + "/visibility",
        Map.of("statsPublic", true),
        200);
    assertThat(number("SELECT stats_public FROM link WHERE id = ?", linkId)).isEqualTo(1);
    assertThat(
            request("link-stats-public", null, "GET", path + "/public-stats", null, 200)
                .path("totalClicks")
                .asLong())
        .isEqualTo(1);
    assertThat(
            request("link-events", owner, "GET", path + "/events?limit=1", null, 200)
                .path("items")
                .size())
        .isEqualTo(1);
    assertThat(
            request(
                    "link-weekly-insights",
                    owner,
                    "GET",
                    "/api/v1/users/me/insights/week",
                    null,
                    200)
                .path("totalClicks")
                .asLong())
        .isEqualTo(1);
    var eventsCsv = raw("link-events-csv", owner, "GET", path + "/events.csv", null, null, 200);
    assertThat(new String(eventsCsv.body(), StandardCharsets.UTF_8).lines().count())
        .isGreaterThanOrEqualTo(2);
    var statsCsv =
        raw("link-stats-csv", owner, "GET", path + "/stats.csv?dimension=daily", null, null, 200);
    assertThat(new String(statsCsv.body(), StandardCharsets.UTF_8)).contains("1");
    raw(
        "link-unlock-view-limit",
        null,
        "POST",
        "/" + code,
        "password=launch-password",
        "application/x-www-form-urlencoded",
        410);
    assertThat(number("SELECT view_count FROM link WHERE id = ?", linkId)).isEqualTo(1);
  }

  @Test
  void utcDailyReadKeepsBoundaryInstantsAcrossDatabaseSessionTimeZones() throws Exception {
    long linkId = linkId(createLink("link-utc-day-fixture-create"));
    Instant from = Instant.parse("2026-09-05T00:00:00Z");
    // Clock fixtures around the seven-day cutoff and UTC midnight, independent of the real visit
    // above.
    for (Instant clickedAt :
        List.of(
            from.minusSeconds(1),
            from,
            Instant.parse("2026-09-10T23:59:59Z"),
            Instant.parse("2026-09-11T00:00:00Z"),
            Instant.parse("2026-09-11T15:00:00Z"))) {
      jdbc.update(
          "INSERT INTO click_event (link_id, clicked_at, is_bot) VALUES (?, FROM_UNIXTIME(?), false)",
          linkId,
          clickedAt.getEpochSecond());
    }
    jdbc.update(
        "INSERT INTO click_event (link_id, clicked_at, is_bot) VALUES (?, FROM_UNIXTIME(?), true)",
        linkId,
        from.getEpochSecond());

    for (String sessionZone : List.of("SYSTEM", "+00:00", "+09:00")) {
      var rows = dailyClicksInSessionZone(linkId, from, sessionZone);
      assertThat(rows)
          .as("UTC dates in session %s", sessionZone)
          .extracting(DailyClickBucketRow::getBucket, DailyClickBucketRow::getCount)
          .containsExactlyInAnyOrder(tuple(0, 1L), tuple(5, 1L), tuple(6, 2L));
      // A fractional Instant cutoff must not be truncated to the previous whole second.
      assertThat(dailyClicksInSessionZone(linkId, from.plusMillis(500), sessionZone))
          .extracting(DailyClickBucketRow::getBucket)
          .containsExactlyInAnyOrder(5, 6);
    }
  }

  private List<DailyClickBucketRow> dailyClicksInSessionZone(
      long linkId, Instant from, String zone) {
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              String originalZone = jdbc.queryForObject("SELECT @@session.time_zone", String.class);
              try {
                jdbc.update("SET SESSION time_zone = ?", zone);
                assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class))
                    .isEqualTo(zone);
                List<Instant> starts =
                    java.util.stream.IntStream.range(0, 7)
                        .mapToObj(
                            i ->
                                i == 0
                                    ? from
                                    : Instant.parse("2026-09-05T00:00:00Z").plusSeconds(i * 86400L))
                        .toList();
                return clickTime.findDailyClickBucketsByLinkIds(
                    List.of(linkId), starts, Instant.parse("2026-09-11T23:59:59.999999Z"));
              } finally {
                jdbc.update("SET SESSION time_zone = ?", originalZone);
              }
            });
  }

  @Test
  void ownerConfiguresThenRemovesAWebhookAndBulkDeletesLinks() throws Exception {
    String code = createLink("link-create-webhook");
    String otherCode = createLink("link-create-bulk-companion");
    String path = "/api/v1/links/" + code;
    // Public IP literal avoids a real DNS lookup; no delivery is triggered in this management flow.
    var issued =
        request(
            "link-webhook-register",
            owner,
            "POST",
            path + "/webhooks",
            Map.of("url", "https://93.184.216.34/hook", "name", "Release hook"),
            201);
    long webhookId = issued.path("id").asLong();
    assertThat(text("SELECT secret FROM link_webhook WHERE id = ?", webhookId))
        .isNotEqualTo(issued.path("secret").asText());
    assertThat(request("link-webhook-list", owner, "GET", path + "/webhooks", null, 200).toString())
        .contains("Release hook")
        .doesNotContain(issued.path("secret").asText());
    request(
        "link-webhook-configure",
        owner,
        "PUT",
        path + "/webhooks/" + webhookId + "/config",
        Map.of("sampleRate", 50, "includeBots", false, "dailyQuota", 20),
        200);
    assertThat(number("SELECT sample_rate FROM link_webhook WHERE id = ?", webhookId))
        .isEqualTo(50);
    request(
        "link-webhook-disable",
        owner,
        "PATCH",
        path + "/webhooks/" + webhookId,
        Map.of("enabled", false),
        200);
    assertThat(number("SELECT enabled FROM link_webhook WHERE id = ?", webhookId)).isZero();
    request("link-webhook-delete", owner, "DELETE", path + "/webhooks/" + webhookId, null, 204);
    assertThat(number("SELECT COUNT(*) FROM link_webhook WHERE id = ?", webhookId)).isZero();
    assertThat(
            request(
                    "link-bulk-delete",
                    owner,
                    "DELETE",
                    "/api/v1/links",
                    Map.of("shortCodes", List.of(code, otherCode)),
                    200)
                .path("deleted")
                .asLong())
        .isEqualTo(2);
    assertThat(number("SELECT COUNT(*) FROM link WHERE short_code IN (?, ?)", code, otherCode))
        .isZero();
  }
}
