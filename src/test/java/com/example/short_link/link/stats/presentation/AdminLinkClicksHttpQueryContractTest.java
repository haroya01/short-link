package com.example.short_link.link.stats.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.application.ClickRollupService;
import com.example.short_link.testsupport.OperationalHttpJourneySupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminLinkClicksHttpQueryContractTest extends OperationalHttpJourneySupport {

  @Autowired private LinkRepository links;
  @Autowired private ClickRollupService rollup;

  @Test
  void anAdminTakesClicksOutOfALinksNumbersAndPutsThemBack() throws Exception {
    Actor admin = actor("clicks-admin", true);
    Actor owner = actor("clicks-owner", false);
    String code = "opsclicks";
    String clicks = "/api/v1/admin/links/" + code + "/clicks";
    long linkId =
        transactions.execute(
            transaction ->
                links
                    .save(
                        new LinkEntity(
                            "https://example.com/clicks", new ShortCode(code), owner.id(), null))
                    .getId());
    Instant now = Instant.now();
    long summedDay = click(linkId, now.minus(Duration.ofDays(3)), null);
    click(linkId, now.minus(Duration.ofDays(3)).plusSeconds(1), null);
    long crawler = click(linkId, now.minus(Duration.ofMinutes(3)), "crawler:x");
    long rawDay = click(linkId, now.minus(Duration.ofMinutes(2)), null);
    rollup.aggregate(now);
    assertThat(summedHumans(linkId)).isEqualTo(2);

    step(
        "ops-click-denies-member",
        "POST",
        clicks + "/exclude",
        owner,
        Map.of("clickIds", List.of(rawDay)),
        403);
    var page = step("ops-click-list", "GET", clicks, admin, null, 200);
    assertThat(page.path("items").valueStream().map(item -> item.path("id").asLong()))
        .startsWith(rawDay, crawler);
    assertThat(page.path("items").valueStream().map(item -> item.path("excluded").asBoolean()))
        .containsOnly(false);

    assertThat(
            step(
                    "ops-click-exclude",
                    "POST",
                    clicks + "/exclude",
                    admin,
                    Map.of("clickIds", List.of(rawDay, crawler)),
                    200)
                .path("changed")
                .asInt())
        .isEqualTo(1);
    assertThat(botName(rawDay)).isEqualTo("preview:excluded");
    assertThat(botName(crawler)).isEqualTo("crawler:x");
    assertThat(summedHumans(linkId)).isEqualTo(2);

    assertThat(
            step(
                    "ops-click-exclude-summed-day",
                    "POST",
                    clicks + "/exclude",
                    admin,
                    Map.of("clickIds", List.of(summedDay)),
                    200)
                .path("changed")
                .asInt())
        .isEqualTo(1);
    assertThat(summedHumans(linkId)).isEqualTo(1);
    assertThat(
            step("ops-click-list-excluded", "GET", clicks, admin, null, 200)
                .path("items")
                .valueStream()
                .filter(item -> item.path("excluded").asBoolean())
                .map(item -> item.path("id").asLong()))
        .containsExactly(rawDay, summedDay);

    assertThat(
            step(
                    "ops-click-restore",
                    "POST",
                    clicks + "/restore",
                    admin,
                    Map.of("clickIds", List.of(rawDay, summedDay, crawler)),
                    200)
                .path("changed")
                .asInt())
        .isEqualTo(2);
    assertThat(botName(rawDay)).isNull();
    assertThat(botName(summedDay)).isNull();
    assertThat(botName(crawler)).isEqualTo("crawler:x");
    assertThat(summedHumans(linkId)).isEqualTo(2);
    assertThat(
            jdbc.queryForList(
                "SELECT action FROM audit_log WHERE target_type = 'link' AND target_id = ?"
                    + " AND actor_user_id = ? ORDER BY id",
                String.class,
                code,
                admin.id()))
        .containsExactly("LINK_CLICKS_EXCLUDED", "LINK_CLICKS_EXCLUDED", "LINK_CLICKS_RESTORED");
  }

  private long click(long linkId, Instant at, String botName) {
    jdbc.update(
        "INSERT INTO click_event (link_id, clicked_at, is_bot, bot_name, device_class,"
            + " country_code, client_ip) VALUES (?, FROM_UNIXTIME(?), ?, ?, 'desktop', 'KR',"
            + " '10.0.0.0')",
        linkId,
        at.getEpochSecond(),
        botName == null ? 0 : 1,
        botName);
    return jdbc.queryForObject(
        "SELECT MAX(id) FROM click_event WHERE link_id = ?", Long.class, linkId);
  }

  private String botName(long clickId) {
    return jdbc.queryForObject(
        "SELECT IF(is_bot = 1, bot_name, NULL) FROM click_event WHERE id = ?",
        String.class,
        clickId);
  }

  private long summedHumans(long linkId) {
    return jdbc.queryForObject(
        "SELECT COALESCE(SUM(humans), 0) FROM click_recent_quarter WHERE link_id = ?",
        Long.class,
        linkId);
  }
}
