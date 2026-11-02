package com.example.short_link.link.stats.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ClickFlusherIntegrationTest {

  private static final String DESKTOP_SAFARI =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15";

  @Autowired private ClickBuffer buffer;
  @Autowired private ClickFlusher flusher;
  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private JdbcTemplate jdbc;

  private Long userId;
  private Long linkId;

  @BeforeEach
  void aLink() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    userId =
        users.save(new UserEntity("flush-" + suffix + "@x.com", "google", "g-f" + suffix)).getId();
    String code = "f" + suffix.substring(0, 7);
    linkId = links.save(new LinkEntity("https://example.com/" + code, code, userId, null)).getId();
  }

  @AfterEach
  void removeSeed() {
    jdbc.update("DELETE FROM click_event WHERE link_id = ?", linkId);
    jdbc.update("DELETE FROM link WHERE id = ?", linkId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  void aRowTheDatabaseRefusesLosesOnlyItselfNotTheClicksSavedBeforeIt() {
    for (int i = 1; i <= 50; i++) {
      String forcedBotName = i == 30 ? "preview:" + "x".repeat(100) : null;
      buffer.offer(
          click(
              "https://example.com/landing",
              "https://news.example/" + i,
              DESKTOP_SAFARI,
              forcedBotName));
    }

    flusher.flush();

    assertThat(clicks()).isEqualTo(49);
  }

  @Test
  void aVisitorsOversizedHeadersStillCountAsOneClick() {
    for (int i = 1; i <= 50; i++) {
      buffer.offer(
          i == 30
              ? click(
                  "https://example.com/landing?utm_content=" + "c".repeat(300),
                  "https://" + ("a".repeat(60) + ".").repeat(5) + "com/",
                  "Mozilla/5.0 (compatible; "
                      + "Z".repeat(120)
                      + "bot/1.0; +https://x.example/bot)",
                  null)
              : click(
                  "https://example.com/landing",
                  "https://news.example/" + i,
                  DESKTOP_SAFARI,
                  null));
    }

    flusher.flush();

    assertThat(clicks()).isEqualTo(50);
  }

  private PendingClick click(
      String destination, String referrer, String userAgent, String forcedBotName) {
    return new PendingClick(
        ClickContext.of(
            new LinkId(linkId), destination, referrer, userAgent, "203.0.113.7", "ko-KR"),
        forcedBotName,
        Instant.now());
  }

  private long clicks() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM click_event WHERE link_id = ?", Long.class, linkId);
  }
}
