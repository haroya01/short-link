package com.example.short_link.link.application;

import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkStatsService {

  private static final Duration DAILY_WINDOW = Duration.ofDays(30);
  private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Seoul");
  private static final String REPORT_TZ = "+09:00";
  private static final Pageable TOP_50 = PageRequest.ofSize(50);

  private final LinkRepository linkRepository;
  private final ClickEventRepository clickRepository;
  private final ReferrerChannelClassifier channelClassifier;

  @Transactional(readOnly = true)
  public LinkStats stats(Long userId, String shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkNotOwnedException(shortCode);
    }
    Long linkId = link.getId();

    long total = clickRepository.countByLinkId(linkId);
    long human = clickRepository.countHumanByLinkId(linkId);
    long bot = clickRepository.countBotByLinkId(linkId);

    List<LinkStats.DailyClick> daily =
        clickRepository
            .findDailyClicks(linkId, Instant.now().minus(DAILY_WINDOW), REPORT_TZ)
            .stream()
            .map(r -> new LinkStats.DailyClick(r.getDay(), r.getCount()))
            .toList();

    List<LinkStats.HourClick> hourly =
        clickRepository.findHourlyClicks(linkId, REPORT_TZ).stream()
            .map(r -> new LinkStats.HourClick(r.getHour(), r.getCount()))
            .toList();

    List<LinkStats.DayOfWeekClick> dayOfWeek =
        clickRepository.findDayOfWeekClicks(linkId, REPORT_TZ).stream()
            .map(r -> new LinkStats.DayOfWeekClick(mapDayOfWeek(r.getDow()), r.getCount()))
            .toList();

    List<LinkStats.ReferrerClick> referrers =
        clickRepository.findReferrerClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.ReferrerClick(r.getReferrer(), r.getCount()))
            .toList();

    Map<String, Long> channelTotals = new HashMap<>();
    referrers.forEach(
        r -> channelTotals.merge(channelClassifier.classify(r.referrer()), r.count(), Long::sum));
    long direct = clickRepository.countDirectByLinkId(linkId);
    if (direct > 0) {
      channelTotals.merge("direct", direct, Long::sum);
    }
    List<LinkStats.ChannelClick> channels =
        channelTotals.entrySet().stream()
            .map(e -> new LinkStats.ChannelClick(e.getKey(), e.getValue()))
            .sorted((a, b) -> Long.compare(b.count(), a.count()))
            .toList();

    List<LinkStats.DeviceClick> devices =
        clickRepository.findDeviceClicks(linkId).stream()
            .map(r -> new LinkStats.DeviceClick(orUnknown(r.getDevice()), r.getCount()))
            .toList();

    List<LinkStats.OsClick> os =
        clickRepository.findOsClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.OsClick(orUnknown(r.getOs()), r.getCount()))
            .toList();

    List<LinkStats.BrowserClick> browsers =
        clickRepository.findBrowserClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.BrowserClick(orUnknown(r.getBrowser()), r.getCount()))
            .toList();

    List<LinkStats.UtmCampaignClick> campaigns =
        clickRepository.findUtmCampaignClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.UtmCampaignClick(r.getCampaign(), r.getCount()))
            .toList();

    return new LinkStats(
        shortCode,
        REPORT_ZONE.getId(),
        total,
        human,
        bot,
        daily,
        hourly,
        dayOfWeek,
        referrers,
        channels,
        devices,
        os,
        browsers,
        campaigns);
  }

  static String mapDayOfWeek(int mysqlDow) {
    return switch (mysqlDow) {
      case 1 -> DayOfWeek.SUNDAY.toString();
      case 2 -> DayOfWeek.MONDAY.toString();
      case 3 -> DayOfWeek.TUESDAY.toString();
      case 4 -> DayOfWeek.WEDNESDAY.toString();
      case 5 -> DayOfWeek.THURSDAY.toString();
      case 6 -> DayOfWeek.FRIDAY.toString();
      case 7 -> DayOfWeek.SATURDAY.toString();
      default -> "UNKNOWN";
    };
  }

  private static String orUnknown(String v) {
    return v == null ? "unknown" : v;
  }
}
