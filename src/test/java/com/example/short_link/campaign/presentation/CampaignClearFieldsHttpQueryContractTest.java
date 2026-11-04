package com.example.short_link.campaign.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.presentation.LinkJourneyHttpSupport;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CampaignClearFieldsHttpQueryContractTest extends LinkJourneyHttpSupport {

  @Test
  void anEmptyStringClearsAFieldNullKeepsItAndRowsHoldingEmptyStringsStillServe() throws Exception {
    long id =
        request(
                "campaign-clear-create",
                owner,
                "POST",
                "/api/v1/campaigns",
                Map.of(
                    "name",
                    "Autumn",
                    "endsAt",
                    Instant.now().plus(30, ChronoUnit.DAYS).toString(),
                    "defaultDestinationUrl",
                    "https://example.com/launch",
                    "postEndAction",
                    "EXPIRE",
                    "postEndMessage",
                    "곧 다시 열어요"),
                201)
            .path("id")
            .asLong();
    String path = "/api/v1/campaigns/" + id;
    var batch =
        request(
            "campaign-clear-batch-create",
            owner,
            "POST",
            path + "/batches",
            Map.of(
                "name",
                "Station",
                "quantity",
                100,
                "distributorName",
                "Team",
                "areaLabel",
                "Tokyo",
                "memo",
                "Entrance A"),
            201);
    long batchId = batch.path("id").asLong();
    long linkId = batch.path("linkId").asLong();

    var cleared =
        request(
            "campaign-clear-fields",
            owner,
            "PATCH",
            path,
            Map.of("defaultDestinationUrl", "", "postEndMessage", ""),
            200);
    assertThat(cleared.path("defaultDestinationUrl").isNull()).isTrue();
    assertThat(cleared.path("postEndMessage").isNull()).isTrue();
    assertThat(cleared.path("postEndAction").asText()).isEqualTo("EXPIRE");
    assertThat(
            number(
                "SELECT COUNT(*) FROM campaign WHERE id = ? AND default_destination_url IS NULL"
                    + " AND post_end_message IS NULL",
                id))
        .isEqualTo(1);

    var kept =
        json.readTree(
            raw(
                    "campaign-clear-null-keeps",
                    owner,
                    "PATCH",
                    path,
                    "{\"name\":\"Autumn renamed\",\"endsAt\":null,\"postEndAction\":null}",
                    "application/json",
                    200)
                .body());
    assertThat(kept.path("name").asText()).isEqualTo("Autumn renamed");
    assertThat(kept.path("postEndAction").asText()).isEqualTo("EXPIRE");
    assertThat(kept.path("endsAt").isNull()).isFalse();

    var batchCleared =
        request(
            "campaign-clear-batch-fields",
            owner,
            "PATCH",
            path + "/batches/" + batchId,
            Map.of("distributorName", "", "memo", ""),
            200);
    assertThat(batchCleared.path("distributorName").isNull()).isTrue();
    assertThat(batchCleared.path("memo").isNull()).isTrue();
    assertThat(batchCleared.path("areaLabel").asText()).isEqualTo("Tokyo");

    jdbc.update(
        "UPDATE campaign SET default_destination_url = '', post_end_destination_url = '',"
            + " post_end_message = '' WHERE id = ?",
        id);
    jdbc.update(
        "UPDATE campaign_batch SET distributor_name = '', area_label = '', memo = '' WHERE id = ?",
        batchId);

    var legacy = request("campaign-clear-legacy-detail", owner, "GET", path, null, 200);
    assertThat(legacy.path("defaultDestinationUrl").isNull()).isTrue();
    assertThat(legacy.path("postEndDestinationUrl").isNull()).isTrue();
    assertThat(legacy.path("postEndMessage").isNull()).isTrue();
    request(
        "campaign-clear-legacy-batch-needs-url",
        owner,
        "POST",
        path + "/batches",
        Map.of("name", "Museum", "quantity", 50),
        400);
    assertThat(number("SELECT COUNT(*) FROM campaign_batch WHERE campaign_id = ?", id))
        .isEqualTo(1);

    String code = batch.path("shortCode").asText();
    var visit = raw("campaign-clear-legacy-visit", null, "GET", "/" + code, null, null, 302);
    assertThat(visit.headers().firstValue("Location")).contains("https://example.com/launch");
    var stats = request("campaign-clear-legacy-stats", owner, "GET", path + "/stats", null, 200);
    assertThat(stats.path("byDistributor").size()).isZero();
    assertThat(stats.path("byArea").size()).isZero();
    assertThat(stats.path("byBatch").size()).isEqualTo(1);
    var csv =
        raw("campaign-clear-legacy-csv", owner, "GET", path + "/batches/csv", null, null, 200);
    assertThat(new String(csv.body(), StandardCharsets.UTF_8).lines())
        .anySatisfy(line -> assertThat(line).startsWith(batchId + ",Station,,,100,"));

    request("campaign-clear-legacy-end", owner, "POST", path + "/end", null, 200);
    Instant endReturnedAt = Instant.now();
    assertThat(
            number(
                "SELECT COUNT(*) FROM link WHERE id = ? AND expires_at IS NOT NULL"
                    + " AND expired_redirect_url IS NULL AND expired_message IS NULL",
                linkId))
        .isEqualTo(1);
    while (Instant.now().isBefore(endReturnedAt.plusSeconds(1))) {
      Thread.sleep(50);
    }
    raw("campaign-clear-legacy-ended-visit", null, "GET", "/" + code, null, null, 410);
  }
}
