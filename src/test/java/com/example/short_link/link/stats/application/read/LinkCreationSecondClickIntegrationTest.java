package com.example.short_link.link.stats.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.analytics.HeatmapCell;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("integration")
class LinkCreationSecondClickIntegrationTest {

  @Autowired private LinkStatsQueryService service;
  @Autowired private LinkRepository links;
  @Autowired private ClickEventRepository clicks;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entities;

  @Test
  void aClickStoredInTheSecondItsLinkWasCreatedIsInEveryTimeBucketTheTotalCounts() {
    UserEntity owner = users.save(new UserEntity("same-second@x.com", "google", "g-samesec"));
    LinkEntity link =
        links.save(new LinkEntity("https://example.com/", "samesec", owner.getId(), null));
    Instant created = createdPartWayThroughASecond(link);
    clicks.save(
        ClickEventEntity.builder()
            .linkId(link.linkId())
            .clickedAt(created)
            .deviceClass("mobile")
            .bot(false)
            .build());

    LinkStats stats = service.stats(owner.getId(), new ShortCode("samesec"));

    assertThat(stats.humanClicks()).isEqualTo(1);
    assertThat(stats.dailyClicks().stream().mapToLong(LinkStats.DailyClick::count).sum())
        .isEqualTo(1);
    assertThat(stats.hourClicks().stream().mapToLong(LinkStats.HourClick::count).sum())
        .isEqualTo(1);
    assertThat(stats.dayOfWeekClicks().stream().mapToLong(LinkStats.DayOfWeekClick::count).sum())
        .isEqualTo(1);
    assertThat(stats.heatmap().stream().mapToLong(HeatmapCell::count).sum()).isEqualTo(1);
  }

  private Instant createdPartWayThroughASecond(LinkEntity link) {
    entities.flush();
    jdbc.update(
        "UPDATE link SET created_at = TIMESTAMPADD(MICROSECOND, 650000, TIMESTAMPADD(SECOND, -300, "
            + "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s'))) WHERE id = ?",
        link.getId());
    entities.clear();
    Instant created = links.findById(link.getId()).orElseThrow().getCreatedAt();
    assertThat(created).isAfter(created.truncatedTo(ChronoUnit.SECONDS));
    return created;
  }
}
