package com.example.short_link.link.stats.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LinkStatsDailyClicksContractTest {
  private static final Instant NOW = Instant.parse("2026-11-10T15:00:00Z");
  private static final List<Instant> EARLIER_CLICKS =
      List.of(
          Instant.parse("2026-10-25T02:00:00Z"),
          Instant.parse("2026-10-28T10:00:00Z"),
          Instant.parse("2026-10-30T16:00:00Z"),
          Instant.parse("2026-11-03T12:00:00Z"),
          Instant.parse("2026-11-03T12:00:00Z"),
          Instant.parse("2026-11-07T20:00:00Z"),
          Instant.parse("2026-11-09T06:00:00Z"));

  @Autowired private UserRepository users;
  @Autowired private LinkRepository links;
  @Autowired private ClickEventRepository clicks;
  @Autowired private LinkStatsQueryService stats;
  @PersistenceContext private EntityManager em;
  @MockitoBean private Clock clock;

  private UserEntity owner;
  private LinkEntity link;

  @BeforeEach
  void aLinkClickedOverTheLastThreeWeeks() {
    when(clock.instant()).thenReturn(NOW);
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    owner = users.save(new UserEntity(suffix + "@x.com", "google", "g-" + suffix));
    link =
        links.save(
            new LinkEntity("https://example.com/" + suffix, "c" + suffix, owner.getId(), null));
    EARLIER_CLICKS.forEach(this::click);
  }

  @ParameterizedTest
  @CsvSource({
    "Asia/Seoul, 2026-11-05T14:59:59.600Z",
    "Asia/Seoul, 2026-11-05T15:00:00Z",
    "Asia/Kathmandu, 2026-11-01T18:14:59.600Z",
    "America/New_York, 2026-11-05T04:59:59.600Z",
  })
  void oneMoreClickRaisesOnlyItsOwnLocalDayAndHourByOne(String zone, Instant at) {
    owner.changeTimezone(zone);
    ZonedDateTime local = at.atZone(ZoneId.of(zone));
    Map<LocalDate, Long> daysBefore = daily();
    Map<Integer, Long> hoursBefore = hourly();

    click(at);

    assertThat(change(daysBefore, daily())).containsExactly(Map.entry(local.toLocalDate(), 1L));
    assertThat(change(hoursBefore, hourly())).containsExactly(Map.entry(local.getHour(), 1L));
  }

  @Test
  void movingTheOwnerToAnotherZoneMovesOnlyTheClicksWhoseLocalDayDiffers() {
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    ZoneId newYork = ZoneId.of("America/New_York");
    Map<LocalDate, Long> moved = new TreeMap<>();
    for (Instant at : EARLIER_CLICKS) {
      moved.merge(at.atZone(seoul).toLocalDate(), -1L, Long::sum);
      moved.merge(at.atZone(newYork).toLocalDate(), 1L, Long::sum);
    }
    moved.values().removeIf(count -> count == 0);
    assertThat(moved).isNotEmpty();

    owner.changeTimezone(seoul.getId());
    Map<LocalDate, Long> inSeoul = daily();
    owner.changeTimezone(newYork.getId());
    Map<LocalDate, Long> inNewYork = daily();

    assertThat(change(inSeoul, inNewYork)).isEqualTo(moved);
  }

  private void click(Instant at) {
    clicks.save(ClickEventEntity.builder().linkId(link.linkId()).clickedAt(at).build());
    em.flush();
  }

  private Map<LocalDate, Long> daily() {
    return report().dailyClicks().stream()
        .collect(Collectors.toMap(LinkStats.DailyClick::date, LinkStats.DailyClick::count));
  }

  private Map<Integer, Long> hourly() {
    return report().hourClicks().stream()
        .collect(Collectors.toMap(LinkStats.HourClick::hour, LinkStats.HourClick::count));
  }

  private LinkStats report() {
    return stats.stats(owner.getId(), link.getShortCode());
  }

  private static <K> Map<K, Long> change(Map<K, Long> before, Map<K, Long> after) {
    Set<K> cells = new HashSet<>(before.keySet());
    cells.addAll(after.keySet());
    Map<K, Long> change = new TreeMap<>();
    for (K cell : cells) {
      long diff = after.getOrDefault(cell, 0L) - before.getOrDefault(cell, 0L);
      if (diff != 0) change.put(cell, diff);
    }
    return change;
  }
}
