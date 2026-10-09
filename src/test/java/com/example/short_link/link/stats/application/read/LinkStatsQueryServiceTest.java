package com.example.short_link.link.stats.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkStatsQueryServiceTest {

  @Test
  void mapsMysqlDayOfWeekToEnumName() {
    assertThat(LinkStatsQueryService.mapDayOfWeek(1)).isEqualTo("SUNDAY");
    assertThat(LinkStatsQueryService.mapDayOfWeek(2)).isEqualTo("MONDAY");
    assertThat(LinkStatsQueryService.mapDayOfWeek(3)).isEqualTo("TUESDAY");
    assertThat(LinkStatsQueryService.mapDayOfWeek(4)).isEqualTo("WEDNESDAY");
    assertThat(LinkStatsQueryService.mapDayOfWeek(5)).isEqualTo("THURSDAY");
    assertThat(LinkStatsQueryService.mapDayOfWeek(6)).isEqualTo("FRIDAY");
    assertThat(LinkStatsQueryService.mapDayOfWeek(7)).isEqualTo("SATURDAY");
  }

  @Test
  void mapsOutOfRangeDayOfWeekToUnknown() {
    assertThat(LinkStatsQueryService.mapDayOfWeek(0)).isEqualTo("UNKNOWN");
    assertThat(LinkStatsQueryService.mapDayOfWeek(8)).isEqualTo("UNKNOWN");
  }

  @Test
  void halfLifeNullWhenEmpty() {
    assertThat(LinkStatsQueryService.halfLife(List.of())).isNull();
  }

  @Test
  void halfLifeIsDayWhereCumulativeReachesHalf() {
    var days =
        List.of(
            new LinkStats.DayClick(0, 30L),
            new LinkStats.DayClick(1, 20L),
            new LinkStats.DayClick(2, 50L));
    assertThat(LinkStatsQueryService.halfLife(days)).isEqualTo(1);
  }

  @Test
  void halfLifeFirstDayWhenItAlreadyReachesHalf() {
    var days = List.of(new LinkStats.DayClick(0, 90L), new LinkStats.DayClick(7, 10L));
    assertThat(LinkStatsQueryService.halfLife(days)).isZero();
  }

  @Test
  void halfLifeNullWhenAllZero() {
    var days = List.of(new LinkStats.DayClick(0, 0L));
    assertThat(LinkStatsQueryService.halfLife(days)).isNull();
  }

  @Test
  void publicStatsDropDestinationUrlsThatTheOwnersReportKeeps() {
    LinkRepository links = mock(LinkRepository.class);
    LinkStatsReportAssembler assembler = mock(LinkStatsReportAssembler.class);
    UserAccessLookup users = mock(UserAccessLookup.class);
    LinkEntity link = new LinkEntity("https://secret.example.com", "pub0001", 7L, null);
    link.changeStatsVisibility(true);
    ShortCode code = new ShortCode("pub0001");
    when(links.findByShortCode(code)).thenReturn(Optional.of(link));
    when(users.timezone(7L)).thenReturn(Optional.empty());
    when(assembler.assemble(any(), any()))
        .thenReturn(
            LinkStats.builder()
                .shortCode(code)
                .totalClicks(3)
                .destinationClicks(
                    List.of(
                        new LinkStats.DestinationClick(
                            null, "https://secret.example.com", "default", 0, true, 1),
                        new LinkStats.DestinationClick(
                            5L, "https://secret.example.com/b", "B", 50, true, 2)))
                .build());
    LinkStatsQueryService service =
        new LinkStatsQueryService(links, users, mock(LinkAccessGuard.class), assembler);

    LinkStats publicView = service.publicStats(code);
    LinkStats ownerView = service.stats(7L, code);

    assertThat(publicView.totalClicks()).isEqualTo(3);
    assertThat(publicView.destinationClicks())
        .containsExactly(
            new LinkStats.DestinationClick(null, null, "default", 0, true, 1),
            new LinkStats.DestinationClick(5L, null, "B", 50, true, 2));
    assertThat(ownerView.destinationClicks())
        .extracting(LinkStats.DestinationClick::url)
        .containsExactly("https://secret.example.com", "https://secret.example.com/b");
  }
}
