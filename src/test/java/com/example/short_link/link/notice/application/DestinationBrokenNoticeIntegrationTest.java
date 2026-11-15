package com.example.short_link.link.notice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.health.application.DestinationCheck;
import com.example.short_link.link.health.application.DestinationCheck.Outcome;
import com.example.short_link.link.health.application.DestinationHealthRecorder;
import com.example.short_link.link.health.domain.DestinationFailure;
import com.example.short_link.link.health.domain.repository.LinkDestinationHealthRepository.DueDestination;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class DestinationBrokenNoticeIntegrationTest {

  private static final DestinationCheck NOT_FOUND =
      new DestinationCheck(Outcome.BROKEN, DestinationFailure.NOT_FOUND, 404);

  @Autowired private DestinationHealthRecorder recorder;
  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private JdbcTemplate jdbc;

  private Long userId;
  private Long linkId;
  private DueDestination due;

  @BeforeEach
  void anOwnedLink() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    userId =
        users.save(new UserEntity("gone-" + suffix + "@x.com", "google", "g-d" + suffix)).getId();
    String code = "d" + suffix.substring(0, 6);
    String url = "https://gone.example.com/" + suffix;
    linkId = links.save(new LinkEntity(url, code, userId, null)).getId();
    due = new DueDestination(linkId, code, userId, url, null);
  }

  @AfterEach
  void removeRows() {
    jdbc.update("DELETE FROM link_notification WHERE recipient_user_id = ?", userId);
    jdbc.update("DELETE FROM link_destination_health WHERE link_id = ?", linkId);
    jdbc.update("DELETE FROM link WHERE id = ?", linkId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  void theOwnerIsToldOnceTheBreakageIsConfirmed() {
    Instant first = Instant.now().minus(Duration.ofHours(30));

    recorder.record(due, NOT_FOUND, first);
    recorder.record(due, NOT_FOUND, first.plus(Duration.ofHours(21)));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(brokenNotices()).isEqualTo(1));
  }

  private long brokenNotices() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM link_notification WHERE recipient_user_id = ? AND type = ?",
        Long.class,
        userId,
        "DESTINATION_BROKEN");
  }
}
