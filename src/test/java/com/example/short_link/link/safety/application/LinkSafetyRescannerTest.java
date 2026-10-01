package com.example.short_link.link.safety.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.domain.repository.LinkRepository.SafetyRescanRow;
import com.example.short_link.link.moderation.application.LinkModerationService;
import com.example.short_link.link.moderation.domain.LinkDisableReason;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkSafetyRescannerTest {

  private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

  @Mock private LinkRepository links;
  @Mock private LinkDestinationRepository destinations;
  @Mock private UrlThreatLookup lookup;
  @Mock private LinkModerationService moderation;

  private LinkSafetyRescanner rescanner;

  @BeforeEach
  void setUp() {
    rescanner = new LinkSafetyRescanner(links, destinations, lookup, moderation);
  }

  private static SafetyRescanRow row(long id, String url) {
    return new SafetyRescanRow() {
      @Override
      public Long getLinkId() {
        return id;
      }

      @Override
      public String getOriginalUrl() {
        return url;
      }
    };
  }

  @Test
  void switchesOffLinksWhoseDestinationOrEnabledVariantNowMatchesAThreat() {
    when(links.findSafetyRescanBatch(0L, NOW, 10))
        .thenReturn(
            List.of(
                row(1, "https://ok.example/"),
                row(2, "https://turned-bad.example/"),
                row(3, "https://ok.example/other")));
    LinkDestinationEntity badVariant =
        new LinkDestinationEntity(new LinkId(3L), "https://bad-variant.example/", 50, "B", null);
    LinkDestinationEntity disabledVariant =
        new LinkDestinationEntity(new LinkId(1L), "https://bad-but-off.example/", 50, "C", null);
    disabledVariant.update(null, null, null, false, null);
    when(destinations.findAllByLinkIdIn(Set.of(1L, 2L, 3L)))
        .thenReturn(List.of(badVariant, disabledVariant));
    when(lookup.unsafeAmong(anyList()))
        .thenReturn(Set.of("https://turned-bad.example/", "https://bad-variant.example/"));
    when(moderation.disable(any(), any(), any())).thenReturn(true);

    LinkSafetyRescanner.Result result = rescanner.rescan(0L, 10, NOW);

    verify(moderation).disable(2L, LinkDisableReason.SAFE_BROWSING, null);
    verify(moderation).disable(3L, LinkDisableReason.SAFE_BROWSING, null);
    verify(moderation, never()).disable(1L, LinkDisableReason.SAFE_BROWSING, null);
    assertThat(result).isEqualTo(new LinkSafetyRescanner.Result(3, 2, 3L, true));
  }

  @Test
  void asksAtMostFiveHundredUrlsPerLookup() {
    List<SafetyRescanRow> batch =
        LongStream.rangeClosed(1, 1200)
            .mapToObj(id -> row(id, "https://site" + id + ".example/"))
            .toList();
    when(links.findSafetyRescanBatch(0L, NOW, 1200)).thenReturn(batch);
    when(destinations.findAllByLinkIdIn(any())).thenReturn(List.of());
    when(lookup.unsafeAmong(anyList())).thenReturn(Set.of());

    LinkSafetyRescanner.Result result = rescanner.rescan(0L, 1200, NOW);

    verify(lookup, times(3)).unsafeAmong(anyList());
    assertThat(result.reachedEnd()).isFalse();
    assertThat(result.lastLinkId()).isEqualTo(1200L);
  }

  @Test
  void anEmptyBatchMeansTheWalkReachedTheEnd() {
    when(links.findSafetyRescanBatch(40L, NOW, 10)).thenReturn(List.of());

    assertThat(rescanner.rescan(40L, 10, NOW))
        .isEqualTo(new LinkSafetyRescanner.Result(0, 0, 40L, true));
  }

  @Test
  void aLookupFailureDisablesNothing() {
    when(links.findSafetyRescanBatch(0L, NOW, 10))
        .thenReturn(List.of(row(1, "https://x.example/")));
    when(destinations.findAllByLinkIdIn(any())).thenReturn(List.of());
    when(lookup.unsafeAmong(anyList()))
        .thenThrow(UrlThreatLookupException.unavailable(new RuntimeException("down")));

    assertThatThrownBy(() -> rescanner.rescan(0L, 10, NOW))
        .isInstanceOf(UrlThreatLookupException.class);
    verify(moderation, never()).disable(any(), any(), any());
  }
}
