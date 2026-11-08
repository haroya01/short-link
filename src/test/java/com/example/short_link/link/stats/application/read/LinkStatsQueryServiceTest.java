package com.example.short_link.link.stats.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.common.time.ReportZones;
import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LinkStatsQueryServiceTest {

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

  private final LinkRepository repo = mock(LinkRepository.class);
  private final LinkStatsReportAssembler reports = mock(LinkStatsReportAssembler.class);
  private final UserAccessLookup people = mock(UserAccessLookup.class);
  private final LinkStatsQueryService guarded =
      new LinkStatsQueryService(repo, people, new LinkAccessGuard(people), reports);
  private final ShortCode owned = new ShortCode("own0001");

  private LinkEntity ownedBySeven() {
    LinkEntity link = new LinkEntity("https://example.com", "own0001", 7L, null);
    when(repo.findByShortCode(owned)).thenReturn(Optional.of(link));
    when(people.timezone(7L)).thenReturn(Optional.of("America/New_York"));
    when(people.timezone(1L)).thenReturn(Optional.of("Asia/Tokyo"));
    when(people.isAdmin(1L)).thenReturn(true);
    when(reports.assemble(any(), any())).thenReturn(LinkStats.builder().shortCode(owned).build());
    return link;
  }

  @Test
  void someoneElseOrNobodyCannotReadALinksStats() {
    ownedBySeven();

    assertThatThrownBy(() -> guarded.stats(9L, owned))
        .isInstanceOfSatisfying(
            LinkException.class,
            e -> assertThat(e.errorCode()).isEqualTo(LinkErrorCode.LINK_NOT_OWNED));
    assertThatThrownBy(() -> guarded.stats(null, owned)).isInstanceOf(LinkException.class);
    verify(reports, never()).assemble(any(), any());
  }

  @Test
  void anAdminReadsTheReportInTheOwnersTimezoneAndTheOwnerInTheirOwn() {
    LinkEntity link = ownedBySeven();

    guarded.stats(1L, owned);
    guarded.stats(7L, owned);

    verify(reports, times(2)).assemble(link, ZoneId.of("America/New_York"));
    verify(reports, never()).assemble(link, ZoneId.of("Asia/Tokyo"));
  }

  @Test
  void aLinkWithoutAnOwnerIsReportedInTheDefaultZone() {
    LinkEntity anonymous = new LinkEntity("https://example.com", "anon001", null, null);
    anonymous.changeStatsVisibility(true);
    ShortCode code = new ShortCode("anon001");
    when(repo.findByShortCode(code)).thenReturn(Optional.of(anonymous));
    when(reports.assemble(any(), any()))
        .thenReturn(LinkStats.builder().shortCode(code).destinationClicks(List.of()).build());

    guarded.publicStats(code);

    verify(reports).assemble(anonymous, ReportZones.DEFAULT_ZONE);
  }
}
