package com.example.short_link.link.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.stats.domain.repository.ClickTimeReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClicksByLinkRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MyLinkReaderTest {
  @Mock private ClickTotalsReadRepository totals;
  @Mock private ClickTimeReadRepository time;
  @Mock private LinkTagLookup tags;
  private MyLinkReader reader;
  private LinkEntity link;
  private static final Instant NOW = Instant.parse("2026-09-11T23:59:59.999999999Z");

  @BeforeEach
  void setUp() {
    reader = new MyLinkReader(totals, time, tags, Clock.fixed(NOW, ZoneOffset.UTC));
    link = new LinkEntity("https://example.com/updated", "read123", 7L, null);
    ReflectionTestUtils.setField(link, "id", 42L);
  }

  @Test
  void readsActualMetadataAndFillsOnlyTheLastSevenDailyBuckets() {
    LinkClickCount total = mock(LinkClickCount.class);
    when(total.getLinkId()).thenReturn(42L);
    when(total.getCount()).thenReturn(12L);
    when(totals.countsByLinkIds(List.of(42L))).thenReturn(List.of(total));
    when(tags.tagNamesByLinkIds(List.of(42L))).thenReturn(Map.of(42L, List.of("launch")));
    LocalDate today = LocalDate.of(2026, 9, 11);
    List<DailyClicksByLinkRow> dailyCounts =
        List.of(daily(today.minusDays(6), 1L), daily(today.minusDays(1), 3L), daily(today, 2L));
    when(time.findDailyClicksByLinkIdsSince(eq(List.of(42L)), any())).thenReturn(dailyCounts);

    var result = reader.read(link);

    assertThat(result.originalUrl()).isEqualTo("https://example.com/updated");
    assertThat(result.clickCount()).isEqualTo(12L);
    assertThat(result.tags()).containsExactly("launch");
    assertThat(result.clicksLast7d()).containsExactly(1L, 0L, 0L, 0L, 0L, 3L, 2L);
    verify(time).findDailyClicksByLinkIdsSince(List.of(42L), Instant.parse("2026-09-05T00:00:00Z"));
  }

  @Test
  void listAssemblyReusesTheCountsThatWereUsedForSorting() {
    when(tags.tagNamesByLinkIds(List.of(42L))).thenReturn(Map.of());
    when(time.findDailyClicksByLinkIdsSince(eq(List.of(42L)), any())).thenReturn(List.of());

    var result = reader.assemble(List.of(link), Map.of(42L, 7L)).getFirst();

    assertThat(result.clickCount()).isEqualTo(7L);
    assertThat(result.tags()).isEmpty();
    assertThat(result.clicksLast7d()).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 0L);
    verifyNoInteractions(totals);
  }

  @Test
  void midnightRolloverCannotSplitTheWindowStartAndItsDayBuckets() {
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenReturn(NOW, NOW.plusNanos(1));
    reader = new MyLinkReader(totals, time, tags, clock);
    when(tags.tagNamesByLinkIds(List.of(42L))).thenReturn(Map.of());
    DailyClicksByLinkRow todayCount = daily(LocalDate.of(2026, 9, 11), 1L);
    when(time.findDailyClicksByLinkIdsSince(eq(List.of(42L)), any()))
        .thenReturn(List.of(todayCount));

    var result = reader.assemble(List.of(link), Map.of(42L, 1L)).getFirst();

    assertThat(result.clicksLast7d()).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 1L);
    verify(clock).instant();
    verify(time).findDailyClicksByLinkIdsSince(List.of(42L), Instant.parse("2026-09-05T00:00:00Z"));
  }

  private static DailyClicksByLinkRow daily(LocalDate day, long count) {
    DailyClicksByLinkRow row = mock(DailyClicksByLinkRow.class);
    when(row.getLinkId()).thenReturn(42L);
    when(row.getDay()).thenReturn(day);
    when(row.getCount()).thenReturn(count);
    return row;
  }
}
