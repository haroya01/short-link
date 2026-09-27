package com.example.short_link.link.stats.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.link.stats.domain.repository.ClickTimeReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClickBucketRow;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DailyClickBucketsIntegrationTest {

  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private ClickEventRepository clicks;
  @Autowired private ClickTimeReadRepository clickTime;
  @PersistenceContext private EntityManager em;

  @Test
  void aClickInTheSecondHalfOfASecondIsCountedByAReadLaterInThatSecond() {
    Instant second = Instant.parse("2026-09-11T11:06:22Z");
    Long linkId = linkClickedAt(second.plusMillis(700));

    assertThat(buckets(linkId, second.plusMillis(900))).containsExactly(Map.entry(6, 1L));
  }

  @Test
  void aClickJustBeforeMidnightStaysOnItsOwnDay() {
    Long linkId = linkClickedAt(Instant.parse("2026-09-10T23:59:59.600Z"));

    assertThat(buckets(linkId, Instant.parse("2026-09-11T12:00:00Z")))
        .containsExactly(Map.entry(5, 1L));
  }

  private Long linkClickedAt(Instant clickedAt) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UserEntity owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    LinkEntity link =
        links.save(
            new LinkEntity("https://example.com/" + suffix, "d" + suffix, owner.getId(), null));
    clicks.save(ClickEventEntity.builder().linkId(link.linkId()).clickedAt(clickedAt).build());
    em.flush();
    return link.getId();
  }

  private Map<Integer, Long> buckets(Long linkId, Instant now) {
    LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
    List<Instant> dayStarts =
        IntStream.range(0, 7)
            .mapToObj(i -> today.minusDays(6 - i).atStartOfDay(ZoneOffset.UTC).toInstant())
            .toList();
    return clickTime.findDailyClickBucketsByLinkIds(List.of(linkId), dayStarts, now).stream()
        .collect(Collectors.toMap(DailyClickBucketRow::getBucket, DailyClickBucketRow::getCount));
  }
}
