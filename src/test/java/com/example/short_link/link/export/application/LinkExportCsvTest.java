package com.example.short_link.link.export.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.classifier.application.ReferrerChannelClassifier;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.export.application.properties.LinkExportProperties;
import com.example.short_link.link.stats.application.LinkClickEventReader;
import com.example.short_link.link.stats.application.read.LinkStatsQueryService;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.support.TestEntities;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkExportCsvTest {

  private static final Long OWNER = 3L;
  private static final Long LINK = 7L;
  private static final ShortCode CODE = new ShortCode("export1");
  private static final String EVENT_HEADER =
      "clicked_at,country,region,city,device,os,browser,channel,referrer_host,language,is_bot,"
          + "bot_name,region_code\n";

  @Mock private LinkRepository linkRepository;
  @Mock private LinkClickEventReader clickEvents;
  @Mock private LinkStatsQueryService statsService;
  @Mock private LinkAccessGuard accessGuard;

  private final LinkEntity link =
      TestEntities.withId(new LinkEntity("https://example.com/", CODE.value(), OWNER, null), LINK);

  @Test
  void anEventRowCarriesEveryColumnInHeaderOrder() {
    LinkExportService service = service(10, 100);
    aLink();
    ClickEventEntity person =
        TestEntities.withId(
            ClickEventEntity.builder()
                .linkId(new LinkId(LINK))
                .clickedAt(Instant.parse("2026-11-14T03:05:09Z"))
                .countryCode("KR")
                .regionName("Seoul")
                .regionCode("KR-11")
                .cityName("Gangnam-gu")
                .deviceClass("mobile")
                .osName("iOS")
                .browserName("Safari")
                .referrer("https://www.instagram.com/p/1")
                .referrerHost("www.instagram.com")
                .language("ko")
                .bot(false)
                .build(),
            2L);
    ClickEventEntity crawler =
        TestEntities.withId(
            ClickEventEntity.builder()
                .linkId(new LinkId(LINK))
                .clickedAt(Instant.parse("2026-11-14T03:05:08Z"))
                .deviceClass("desktop")
                .bot(true)
                .botName("Googlebot")
                .build(),
            1L);
    when(clickEvents.latest(LINK, 10)).thenReturn(List.of(person, crawler));

    String csv = service.exportEventsCsv(OWNER, CODE);

    assertThat(csv)
        .isEqualTo(
            EVENT_HEADER
                + "2026-11-14T03:05:09Z,KR,Seoul,Gangnam-gu,mobile,iOS,Safari,social,"
                + "www.instagram.com,ko,false,,KR-11\n"
                + "2026-11-14T03:05:08Z,,,,desktop,,,direct,,,true,Googlebot,\n");
    verify(accessGuard).requireView(OWNER, link);
  }

  @Test
  void theEventExportStopsAtTheCapWithoutReadingAnotherBatch() {
    LinkExportService service = service(2, 3);
    aLink();
    when(clickEvents.latest(LINK, 2)).thenReturn(List.of(click(5), click(4)));
    when(clickEvents.before(LINK, 4L, 2)).thenReturn(List.of(click(3), click(2)));

    String csv = service.exportEventsCsv(OWNER, CODE);

    assertThat(csv.lines().skip(1)).hasSize(3);
    verify(clickEvents).latest(LINK, 2);
    verify(clickEvents).before(LINK, 4L, 2);
    verifyNoMoreInteractions(clickEvents);
  }

  @Test
  void theEventExportStopsAfterABatchThatCameBackShort() {
    LinkExportService service = service(2, 100);
    aLink();
    when(clickEvents.latest(LINK, 2)).thenReturn(List.of(click(3), click(2)));
    when(clickEvents.before(LINK, 2L, 2)).thenReturn(List.of(click(1)));

    String csv = service.exportEventsCsv(OWNER, CODE);

    assertThat(csv.lines().skip(1)).hasSize(3);
    verify(clickEvents).before(LINK, 2L, 2);
    verifyNoMoreInteractions(clickEvents);
  }

  @Test
  void aLinkWithoutClicksExportsOnlyTheHeader() {
    LinkExportService service = service(2, 100);
    aLink();
    when(clickEvents.latest(LINK, 2)).thenReturn(List.of());

    assertThat(service.exportEventsCsv(OWNER, CODE)).isEqualTo(EVENT_HEADER);
  }

  @ParameterizedTest
  @MethodSource("dimensions")
  void aStatsExportWritesItsDimensionsHeaderThenEveryRowInOrder(String dimension, String csv) {
    when(statsService.stats(OWNER, CODE)).thenReturn(stats());

    assertThat(service(10, 100).exportStatsCsv(OWNER, CODE, dimension)).isEqualTo(csv);
  }

  static Stream<Arguments> dimensions() {
    return Stream.of(
        Arguments.of("daily", "date,count\n2026-11-14,3\n2026-11-15,1\n"),
        Arguments.of("hourly", "hour,count\n9,2\n21,5\n"),
        Arguments.of("country", "country,count\nKR,4\nJP,1\n"),
        Arguments.of("city", "city,count\nSeoul,4\nTokyo,1\n"),
        Arguments.of("device", "device,count\nmobile,3\ndesktop,2\n"),
        Arguments.of("os", "os,count\niOS,3\nWindows,2\n"),
        Arguments.of("browser", "browser,count\nSafari,3\nChrome,2\n"),
        Arguments.of(
            "referrer",
            "referrer,count\nhttps://www.youtube.com/watch,2\nhttps://news.example/a,1\n"),
        Arguments.of("channel", "channel,count\ndirect,3\nsocial,2\n"));
  }

  @Test
  void aStatsExportDefaultsToDailyAndReadsTheDimensionCaseInsensitively() {
    when(statsService.stats(OWNER, CODE)).thenReturn(stats());
    LinkExportService service = service(10, 100);

    assertThat(service.exportStatsCsv(OWNER, CODE, null))
        .isEqualTo("date,count\n2026-11-14,3\n2026-11-15,1\n");
    assertThat(service.exportStatsCsv(OWNER, CODE, "COUNTRY"))
        .isEqualTo("country,count\nKR,4\nJP,1\n");
  }

  @Test
  void aStatsExportRejectsADimensionItDoesNotKnow() {
    when(statsService.stats(OWNER, CODE)).thenReturn(stats());

    assertThatThrownBy(() -> service(10, 100).exportStatsCsv(OWNER, CODE, "weekday"))
        .isInstanceOfSatisfying(
            LinkException.class,
            failure ->
                assertThat(failure.errorCode()).isEqualTo(LinkErrorCode.INVALID_EXPORT_DIMENSION));
  }

  private LinkExportService service(int eventBatch, int eventCap) {
    return new LinkExportService(
        linkRepository,
        clickEvents,
        new ReferrerChannelClassifier(),
        statsService,
        accessGuard,
        new LinkExportProperties(eventBatch, eventCap));
  }

  private void aLink() {
    when(linkRepository.findByShortCode(CODE)).thenReturn(Optional.of(link));
  }

  private static ClickEventEntity click(long id) {
    return TestEntities.withId(
        ClickEventEntity.builder()
            .linkId(new LinkId(LINK))
            .clickedAt(Instant.parse("2026-11-14T00:00:00Z").plusSeconds(id))
            .deviceClass("desktop")
            .bot(false)
            .build(),
        id);
  }

  private static LinkStats stats() {
    return LinkStats.builder()
        .dailyClicks(
            List.of(
                new LinkStats.DailyClick(LocalDate.of(2026, 11, 14), 3),
                new LinkStats.DailyClick(LocalDate.of(2026, 11, 15), 1)))
        .hourClicks(List.of(new LinkStats.HourClick(9, 2), new LinkStats.HourClick(21, 5)))
        .countryClicks(
            List.of(new LinkStats.CountryClick("KR", 4), new LinkStats.CountryClick("JP", 1)))
        .cityClicks(
            List.of(new LinkStats.CityClick("Seoul", 4), new LinkStats.CityClick("Tokyo", 1)))
        .deviceClicks(
            List.of(
                new LinkStats.DeviceClick("mobile", 3), new LinkStats.DeviceClick("desktop", 2)))
        .osClicks(List.of(new LinkStats.OsClick("iOS", 3), new LinkStats.OsClick("Windows", 2)))
        .browserClicks(
            List.of(
                new LinkStats.BrowserClick("Safari", 3), new LinkStats.BrowserClick("Chrome", 2)))
        .referrerClicks(
            List.of(
                new LinkStats.ReferrerClick("https://www.youtube.com/watch", 2),
                new LinkStats.ReferrerClick("https://news.example/a", 1)))
        .channelClicks(
            List.of(
                new LinkStats.ChannelClick("direct", 3), new LinkStats.ChannelClick("social", 2)))
        .build();
  }
}
