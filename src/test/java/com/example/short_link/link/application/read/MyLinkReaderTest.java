package com.example.short_link.link.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.stats.domain.repository.ClickTimeReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DailyClickBucketRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    List<DailyClickBucketRow> dailyCounts = List.of(daily(0, 1L), daily(5, 3L), daily(6, 2L));
    when(time.findDailyClickBucketsByLinkIds(eq(List.of(42L)), any(), eq(NOW)))
        .thenReturn(dailyCounts);

    var result = reader.read(link);

    assertThat(result.originalUrl()).isEqualTo("https://example.com/updated");
    assertThat(result.clickCount()).isEqualTo(12L);
    assertThat(result.tags()).containsExactly("launch");
    assertThat(result.clicksLast7d()).containsExactly(1L, 0L, 0L, 0L, 0L, 3L, 2L);
    verify(time)
        .findDailyClickBucketsByLinkIds(
            eq(List.of(42L)),
            org.mockito.ArgumentMatchers.argThat(
                starts -> starts.getFirst().equals(Instant.parse("2026-09-05T00:00:00Z"))),
            eq(NOW));
  }

  @Test
  void listAssemblyReusesTheCountsThatWereUsedForSorting() {
    when(tags.tagNamesByLinkIds(List.of(42L))).thenReturn(Map.of());
    when(time.findDailyClickBucketsByLinkIds(eq(List.of(42L)), any(), eq(NOW)))
        .thenReturn(List.of());

    var result = reader.assemble(List.of(link), Map.of(42L, 7L)).getFirst();

    assertThat(result.clickCount()).isEqualTo(7L);
    assertThat(result.tags()).isEmpty();
    assertThat(result.clicksLast7d()).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 0L);
    verify(totals, org.mockito.Mockito.never()).countsByLinkIds(any());
  }

  @Test
  void midnightRolloverCannotSplitTheWindowStartAndItsDayBuckets() {
    Clock clock = mock(Clock.class);
    when(clock.instant()).thenReturn(NOW, NOW.plusNanos(1));
    reader = new MyLinkReader(totals, time, tags, clock);
    when(tags.tagNamesByLinkIds(List.of(42L))).thenReturn(Map.of());
    DailyClickBucketRow todayCount = daily(6, 1L);
    when(time.findDailyClickBucketsByLinkIds(eq(List.of(42L)), any(), eq(NOW)))
        .thenReturn(List.of(todayCount));

    var result = reader.assemble(List.of(link), Map.of(42L, 1L)).getFirst();

    assertThat(result.clicksLast7d()).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 1L);
    verify(clock).instant();
    verify(time)
        .findDailyClickBucketsByLinkIds(
            eq(List.of(42L)),
            org.mockito.ArgumentMatchers.argThat(
                starts -> starts.getFirst().equals(Instant.parse("2026-09-05T00:00:00Z"))),
            eq(NOW));
  }

  @Test
  void accountDayBoundariesFollowDaylightSavingInsteadOfAssumingTwentyFourHours() {
    Instant now = Instant.parse("2026-03-10T12:00:00Z");
    UserAccessLookup users = mock(UserAccessLookup.class);
    when(users.timezone(7L)).thenReturn(Optional.of("America/New_York"));
    reader = new MyLinkReader(totals, time, tags, Clock.fixed(now, ZoneId.of("UTC")), users);
    when(tags.tagNamesByLinkIds(List.of(42L))).thenReturn(Map.of());
    DailyClickBucketRow today = daily(6, 2L);
    when(time.findDailyClickBucketsByLinkIds(eq(List.of(42L)), any(), eq(now)))
        .thenReturn(List.of(today));
    link.updateNote("행사 링크");

    var result = reader.assemble(List.of(link), Map.of(42L, 2L)).getFirst();

    assertThat(result.timezone()).isEqualTo("America/New_York");
    assertThat(result.note()).isEqualTo("행사 링크");
    assertThat(result.clicksLast7d().getLast()).isEqualTo(2L);
    ArgumentCaptor<List<Instant>> starts = ArgumentCaptor.forClass(List.class);
    verify(time).findDailyClickBucketsByLinkIds(eq(List.of(42L)), starts.capture(), eq(now));
    assertThat(starts.getValue()).hasSize(7);
    assertThat(Duration.between(starts.getValue().get(4), starts.getValue().get(5)))
        .isEqualTo(Duration.ofHours(23));
    assertThat(starts.getValue().getLast()).isEqualTo(Instant.parse("2026-03-10T04:00:00Z"));
  }

  private static DailyClickBucketRow daily(int bucket, long count) {
    DailyClickBucketRow row = mock(DailyClickBucketRow.class);
    when(row.getLinkId()).thenReturn(42L);
    when(row.getBucket()).thenReturn(bucket);
    when(row.getCount()).thenReturn(count);
    return row;
  }
}
