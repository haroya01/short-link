package com.example.short_link.campaign.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.presentation.LinkJourneyHttpSupport;
import com.example.short_link.link.stats.application.ClickFlusher;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A campaign owner distributes persisted tracking links and reapplies the policy after ending. */
class CampaignLifecycleHttpQueryContractTest extends LinkJourneyHttpSupport {
  @Autowired private ClickFlusher clickFlusher;

  @Test
  void distributesBatchesExportsQrCodesAndEndsTheCampaignWithoutLosingTrackingLinks()
      throws Exception {
    long id = createCampaign("campaign-create", "Autumn launch");
    long comparisonId = createCampaign("campaign-create-comparison", "Earlier launch");
    String path = "/api/v1/campaigns/" + id;
    assertThat(request("campaign-list", owner, "GET", "/api/v1/campaigns", null, 200).size())
        .isEqualTo(2);
    assertThat(request("campaign-detail", owner, "GET", path, null, 200).path("name").asText())
        .isEqualTo(text("SELECT name FROM campaign WHERE id = ?", id));
    request("campaign-detail-foreign-denied", stranger, "GET", path, null, 404);

    var first =
        request(
            "campaign-batch-create", owner, "POST", path + "/batches", batch("Station", 100), 201);
    long batchId = first.path("id").asLong();
    long linkId = first.path("linkId").asLong();
    assertThat(number("SELECT link_id FROM campaign_batch WHERE id = ?", batchId))
        .isEqualTo(linkId);
    assertThat(text("SELECT original_url FROM link WHERE id = ?", linkId))
        .isEqualTo(first.path("destinationUrl").asText());
    var bulk =
        request(
            "campaign-batch-bulk",
            owner,
            "POST",
            path + "/batches/bulk",
            Map.of("batches", List.of(batch("University", 200), batch("Museum", 50))),
            201);
    assertThat(bulk.size()).isEqualTo(2);
    assertThat(number("SELECT COUNT(*) FROM campaign_batch WHERE campaign_id = ?", id))
        .isEqualTo(3);
    assertThat(request("campaign-batch-list", owner, "GET", path + "/batches", null, 200).size())
        .isEqualTo(3);
    assertThat(
            request("campaign-batch-detail", owner, "GET", path + "/batches/" + batchId, null, 200)
                .path("linkId")
                .asLong())
        .isEqualTo(linkId);
    request(
        "campaign-batch-update",
        owner,
        "PATCH",
        path + "/batches/" + batchId,
        Map.of("name", "Central station", "quantity", 150, "memo", "Entrance A"),
        200);
    assertThat(number("SELECT quantity FROM campaign_batch WHERE id = ?", batchId)).isEqualTo(150);
    assertThat(number("SELECT link_id FROM campaign_batch WHERE id = ?", batchId))
        .isEqualTo(linkId);

    byte[] png =
        raw(
                "campaign-batch-qr",
                owner,
                "GET",
                path + "/batches/" + batchId + "/qr?size=128",
                null,
                null,
                200)
            .body();
    assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    byte[] zip =
        raw(
                "campaign-batches-qr-zip",
                owner,
                "GET",
                path + "/batches/qr-zip?size=128",
                null,
                null,
                200)
            .body();
    try (var entries = new ZipInputStream(new ByteArrayInputStream(zip))) {
      int count = 0;
      while (entries.getNextEntry() != null) count++;
      assertThat(count).isEqualTo(3);
    }
    var csv = raw("campaign-batches-csv", owner, "GET", path + "/batches/csv", null, null, 200);
    assertThat(new String(csv.body(), StandardCharsets.UTF_8))
        .contains("Central station", "University", "Museum");
    var visit =
        raw(
            "campaign-batch-visit",
            null,
            "GET",
            "/" + first.path("shortCode").asText(),
            null,
            null,
            302);
    assertThat(visit.headers().firstValue("Location"))
        .contains(first.path("destinationUrl").asText());
    captures.add(
        contracts.captureBackgroundDelivery(
            "campaign-click-worker-persist",
            () -> {
              clickFlusher.flush();
              return "completed";
            }));
    assertThat(number("SELECT COUNT(*) FROM click_event WHERE link_id = ?", linkId)).isEqualTo(1);
    var statistics = request("campaign-stats", owner, "GET", path + "/stats", null, 200);
    assertThat(statistics.toString()).contains("Central station");
    assertThat(statistics.path("totalClicks").asLong()).isEqualTo(1);
    request("campaign-recommendations", owner, "GET", path + "/recommendations", null, 200);
    request(
        "campaign-stats-compare",
        owner,
        "POST",
        "/api/v1/campaigns/stats/compare",
        Map.of("campaignIds", List.of(id, comparisonId)),
        200);

    request(
        "campaign-policy-update",
        owner,
        "PATCH",
        path,
        Map.of(
            "name",
            "Autumn complete",
            "postEndAction",
            "REDIRECT",
            "postEndDestinationUrl",
            "https://example.com/next-launch"),
        200);
    assertThat(text("SELECT post_end_action FROM campaign WHERE id = ?", id)).isEqualTo("REDIRECT");
    request("campaign-end", owner, "POST", path + "/end", null, 200);
    assertThat(text("SELECT status FROM campaign WHERE id = ?", id)).isEqualTo("ENDED");
    assertThat(text("SELECT expired_redirect_url FROM link WHERE id = ?", linkId))
        .isEqualTo("https://example.com/next-launch");
    request(
        "campaign-ended-policy-update",
        owner,
        "PATCH",
        path,
        Map.of("postEndDestinationUrl", "https://example.com/follow-up"),
        200);
    assertThat(text("SELECT post_end_destination_url FROM campaign WHERE id = ?", id))
        .isEqualTo("https://example.com/follow-up");
    assertThat(text("SELECT expired_redirect_url FROM link WHERE id = ?", linkId))
        .isEqualTo("https://example.com/next-launch");
    request("campaign-policy-reapply", owner, "POST", path + "/reapply-policy", null, 200);
    assertThat(text("SELECT expired_redirect_url FROM link WHERE id = ?", linkId))
        .isEqualTo("https://example.com/follow-up");
    request("campaign-batch-delete", owner, "DELETE", path + "/batches/" + batchId, null, 204);
    assertThat(number("SELECT COUNT(*) FROM campaign_batch WHERE id = ?", batchId)).isZero();
    request("campaign-archive", owner, "DELETE", path, null, 200);
    assertThat(text("SELECT status FROM campaign WHERE id = ?", id)).isEqualTo("ARCHIVED");
  }

  private long createCampaign(String contractId, String name) throws Exception {
    long id =
        request(
                contractId,
                owner,
                "POST",
                "/api/v1/campaigns",
                Map.of(
                    "name",
                    name,
                    "endsAt",
                    Instant.now().plus(30, ChronoUnit.DAYS).toString(),
                    "defaultDestinationUrl",
                    "https://example.com/launch",
                    "postEndAction",
                    "KEEP"),
                201)
            .path("id")
            .asLong();
    assertThat(number("SELECT owner_id FROM campaign WHERE id = ?", id)).isEqualTo(owner.id());
    return id;
  }

  private Map<String, Object> batch(String name, int quantity) {
    return Map.of(
        "name", name, "quantity", quantity, "distributorName", "Team", "areaLabel", "Tokyo");
  }
}
