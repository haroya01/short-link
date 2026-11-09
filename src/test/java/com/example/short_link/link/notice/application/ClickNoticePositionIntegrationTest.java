package com.example.short_link.link.notice.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.application.dto.SavedClick;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class ClickNoticePositionIntegrationTest {

  @Autowired private LinkClickNoticeListener listener;
  @Autowired private ClickEventRepository clicks;
  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private JdbcTemplate jdbc;

  private Long userId;
  private Long linkId;
  private String code;

  @BeforeEach
  void aNewLink() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    userId =
        users.save(new UserEntity("first-" + suffix + "@x.com", "google", "g-n" + suffix)).getId();
    code = "n" + suffix.substring(0, 7);
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
  void twoPeopleSavedInOneBatchStillMakeOneFirstClickNotice() {
    List<SavedClick> batch = List.of(person(), person());

    batch.forEach(this::afterCommit);

    assertThat(notices("FIRST_CLICK")).isEqualTo(1);
  }

  @Test
  void aBatchThatReachesTheTenthClickMakesOneMilestoneNotice() {
    for (int i = 0; i < 8; i++) {
      afterCommit(person());
    }
    List<SavedClick> batch = List.of(person(), person());

    batch.forEach(this::afterCommit);

    assertThat(notices("FIRST_CLICK")).isEqualTo(1);
    assertThat(notices("MILESTONE")).isEqualTo(1);
  }

  private SavedClick person() {
    Instant now = Instant.now();
    ClickEventEntity saved =
        clicks.save(
            ClickEventEntity.builder()
                .linkId(new LinkId(linkId))
                .clickedAt(now)
                .referrerHost("news.example")
                .deviceClass("desktop")
                .bot(false)
                .build());
    ClickRecordedEvent click =
        new ClickRecordedEvent(
            new LinkId(linkId), code, userId, now, null, "desktop", "news.example", false, null);
    return new SavedClick(click, saved.getId());
  }

  private void afterCommit(SavedClick saved) {
    LinkClickNoticeListener target = AopTestUtils.getTargetObject(listener);
    target.onClickRecorded(saved);
  }

  private long notices(String type) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM link_notification WHERE recipient_user_id = ? AND type = ?",
        Long.class,
        userId,
        type);
  }
}
