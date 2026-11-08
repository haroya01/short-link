package com.example.short_link.link.notice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.application.ClickBuffer;
import com.example.short_link.link.stats.application.ClickContext;
import com.example.short_link.link.stats.application.ClickFlusher;
import com.example.short_link.link.stats.application.PendingClick;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=8")
class ClickNoticePositionIntegrationTest {

  private static final String DESKTOP_SAFARI =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko)"
          + " Version/17.0 Safari/605.1.15";

  @Autowired private ClickBuffer buffer;
  @Autowired private ClickFlusher flusher;
  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private JdbcTemplate jdbc;

  private Long userId;
  private Long linkId;
  private int visitor;

  @BeforeEach
  void aNewLink() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    userId =
        users.save(new UserEntity("first-" + suffix + "@x.com", "google", "g-n" + suffix)).getId();
    String code = "n" + suffix.substring(0, 7);
    linkId = links.save(new LinkEntity("https://example.com/" + code, code, userId, null)).getId();
  }

  @AfterEach
  void removeSeed() {
    jdbc.update("DELETE FROM link_notification WHERE recipient_user_id = ?", userId);
    jdbc.update("DELETE FROM click_event WHERE link_id = ?", linkId);
    jdbc.update("DELETE FROM link WHERE id = ?", linkId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  void twoPeopleOpeningANewLinkInTheSameSecondStillMakeOneFirstClickNotice() {
    buffer.offer(person());
    buffer.offer(person());
    flusher.flush();

    await()
        .during(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(6))
        .untilAsserted(() -> assertThat(notices("FIRST_CLICK")).isEqualTo(1));
  }

  @Test
  void aBatchThatReachesTheTenthClickMakesOneMilestoneNotice() {
    for (int i = 0; i < 8; i++) {
      buffer.offer(person());
      flusher.flush();
    }
    buffer.offer(person());
    buffer.offer(person());
    flusher.flush();

    await()
        .during(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(6))
        .untilAsserted(() -> assertThat(notices("MILESTONE")).isEqualTo(1));
  }

  private PendingClick person() {
    visitor++;
    return new PendingClick(
        ClickContext.of(
            new LinkId(linkId),
            "https://example.com/landing",
            "https://news.example/",
            DESKTOP_SAFARI,
            "198.51.100." + visitor,
            "ko-KR"),
        null,
        Instant.now());
  }

  private long notices(String type) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM link_notification WHERE recipient_user_id = ? AND type = ?",
        Long.class,
        userId,
        type);
  }
}
