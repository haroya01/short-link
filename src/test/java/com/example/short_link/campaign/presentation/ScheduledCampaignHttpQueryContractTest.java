package com.example.short_link.campaign.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.campaign.domain.repository.CampaignRepository;
import com.example.short_link.campaign.scheduler.CampaignLifecycleJob;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.presentation.LinkJourneyHttpSupport;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Scheduled transitions use the production job and Redis lock, with due times prepared in MySQL.
 */
@TestPropertySource(properties = "short-link.campaign.lifecycle-enabled=true")
class ScheduledCampaignHttpQueryContractTest extends LinkJourneyHttpSupport {
  private static final String LIFECYCLE_LOCK = "kurl:campaign:lifecycle";

  @Autowired private CampaignLifecycleJob lifecycle;
  @Autowired private StringRedisTemplate redis;
  @Autowired private CampaignRepository campaigns;
  @Autowired private LinkRepository links;

  @Test
  void scheduledCampaignStartsAndExpiresItsPrintedLinkWithoutManualStatusChanges()
      throws Exception {
    Instant plannedStart = Instant.now().plus(1, ChronoUnit.DAYS);
    var created =
        request(
            "campaign-scheduled-create",
            owner,
            "POST",
            "/api/v1/campaigns",
            Map.of(
                "name",
                "Scheduled launch",
                "startsAt",
                plannedStart.toString(),
                "endsAt",
                plannedStart.plus(1, ChronoUnit.DAYS).toString(),
                "defaultDestinationUrl",
                "https://example.com/scheduled-launch",
                "postEndAction",
                "EXPIRE",
                "postEndMessage",
                "This launch has ended"),
            201);
    long id = created.path("id").asLong();
    String path = "/api/v1/campaigns/" + id;
    assertThat(created.path("status").asText()).isEqualTo("DRAFT");
    var batch =
        request(
            "campaign-scheduled-batch-create",
            owner,
            "POST",
            path + "/batches",
            Map.of("name", "Printed invitation", "quantity", 100),
            201);
    long linkId = batch.path("linkId").asLong();
    String code = batch.path("shortCode").asText();
    assertThat(number("SELECT COUNT(*) FROM link WHERE id = ? AND expires_at IS NULL", linkId))
        .isEqualTo(1);

    // Simulate time passing by changing only the persisted schedule, outside measured work.
    Instant dueStart = Instant.now().minus(2, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
    setScheduleTime("UPDATE campaign SET starts_at = ? WHERE id = ?", dueStart, id);
    assertThat(campaigns.findById(id).orElseThrow().getStartsAt()).isEqualTo(dueStart);
    redis.opsForValue().set(LIFECYCLE_LOCK, "another-worker", Duration.ofMinutes(1));
    tick("campaign-scheduled-contended-tick");
    assertThat(text("SELECT status FROM campaign WHERE id = ?", id)).isEqualTo("DRAFT");
    assertThat(redis.opsForValue().get(LIFECYCLE_LOCK)).isEqualTo("another-worker");
    redis.delete(LIFECYCLE_LOCK);

    tick("campaign-scheduled-activate-tick");
    assertThat(redis.hasKey(LIFECYCLE_LOCK)).isFalse();
    assertThat(
            request("campaign-scheduled-read-active", owner, "GET", path, null, 200)
                .path("status")
                .asText())
        .isEqualTo("ACTIVE");
    assertThat(text("SELECT status FROM campaign WHERE id = ?", id)).isEqualTo("ACTIVE");
    assertThat(number("SELECT COUNT(*) FROM link WHERE id = ? AND expires_at IS NULL", linkId))
        .isEqualTo(1);

    Instant dueEnd = Instant.now().minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
    setScheduleTime("UPDATE campaign SET ends_at = ? WHERE id = ?", dueEnd, id);
    assertThat(campaigns.findById(id).orElseThrow().getEndsAt()).isEqualTo(dueEnd);
    tick("campaign-scheduled-end-tick");
    assertThat(redis.hasKey(LIFECYCLE_LOCK)).isFalse();
    assertThat(
            request("campaign-scheduled-read-ended", owner, "GET", path, null, 200)
                .path("status")
                .asText())
        .isEqualTo("ENDED");
    assertThat(number("SELECT COUNT(*) FROM link WHERE id = ? AND expires_at IS NOT NULL", linkId))
        .isEqualTo(1);
    assertThat(text("SELECT expired_message FROM link WHERE id = ?", linkId))
        .isEqualTo("This launch has ended");
    assertThat(text("SELECT expired_message FROM link_expiration_policy WHERE link_id = ?", linkId))
        .isEqualTo("This launch has ended");
    awaitStoredExpiration(id, linkId);
    var expired =
        raw("campaign-scheduled-expired-visitor", null, "GET", "/" + code, null, null, 410);
    assertThat(new String(expired.body(), StandardCharsets.UTF_8))
        .contains("This launch has ended");

    String endedAt = text("SELECT CAST(ended_at AS CHAR) FROM campaign WHERE id = ?", id);
    long linkVersion = number("SELECT version FROM link WHERE id = ?", linkId);
    tick("campaign-scheduled-repeat-tick");
    assertThat(text("SELECT CAST(ended_at AS CHAR) FROM campaign WHERE id = ?", id))
        .isEqualTo(endedAt);
    assertThat(number("SELECT version FROM link WHERE id = ?", linkId)).isEqualTo(linkVersion);
    assertThat(redis.hasKey(LIFECYCLE_LOCK)).isFalse();
  }

  private void awaitStoredExpiration(long campaignId, long linkId) throws InterruptedException {
    Instant endedAt = campaigns.findById(campaignId).orElseThrow().getEndedAt();
    Instant expiresAt = links.findById(linkId).orElseThrow().getExpiresAt();
    // link.expires_at is TIMESTAMP without fractional seconds, so MySQL can round it forward.
    // Check the stored policy, then cross that real boundary before making the visitor request.
    assertThat(Duration.between(endedAt, expiresAt).abs()).isLessThan(Duration.ofSeconds(1));
    long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
    while (Instant.now().isBefore(expiresAt)) {
      if (System.nanoTime() >= deadline) {
        throw new AssertionError(
            "Stored campaign-link expiration was not reached within two seconds");
      }
      Thread.sleep(10);
    }
  }

  private void setScheduleTime(String sql, Instant time, long campaignId) {
    // Hibernate stores Instant as UTC. Match that binding when preparing temporal fixtures with
    // plain JDBC, whose default Timestamp binding would use the connection's Asia/Seoul zone.
    assertThat(
            jdbc.update(
                sql,
                statement -> {
                  statement.setTimestamp(
                      1, Timestamp.from(time), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
                  statement.setLong(2, campaignId);
                }))
        .isEqualTo(1);
  }

  private void tick(String contractId) throws Exception {
    captures.add(
        contracts.captureBackgroundDelivery(
            contractId,
            () -> {
              lifecycle.tick();
              return "completed";
            }));
  }
}
