package com.example.short_link.link.application;

import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkStatsService {

  private static final Duration DAILY_WINDOW = Duration.ofDays(30);

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
        clickRepository.findDailyClicks(linkId, Instant.now().minus(DAILY_WINDOW)).stream()
            .map(r -> new LinkStats.DailyClick(r.getDay().toLocalDate(), r.getCnt()))
            .toList();

    List<LinkStats.HourClick> hourly =
        clickRepository.findHourlyClicks(linkId).stream()
            .map(r -> new LinkStats.HourClick(r.getHour(), r.getCnt()))
            .toList();

    List<LinkStats.DayOfWeekClick> dayOfWeek =
        clickRepository.findDayOfWeekClicks(linkId).stream()
            .map(r -> new LinkStats.DayOfWeekClick(mapDayOfWeek(r.getDow()), r.getCnt()))
            .toList();

    List<LinkStats.ReferrerClick> referrers =
        clickRepository.findReferrerClicks(linkId).stream()
            .map(r -> new LinkStats.ReferrerClick(r.getReferrer(), r.getCnt()))
            .toList();

    Map<String, Long> channelTotals = new HashMap<>();
    referrers.forEach(
        r -> channelTotals.merge(channelClassifier.classify(r.referrer()), r.count(), Long::sum));
    List<LinkStats.ChannelClick> channels =
        channelTotals.entrySet().stream()
            .map(e -> new LinkStats.ChannelClick(e.getKey(), e.getValue()))
            .sorted((a, b) -> Long.compare(b.count(), a.count()))
            .toList();

    List<LinkStats.DeviceClick> devices =
        clickRepository.findDeviceClicks(linkId).stream()
            .map(r -> new LinkStats.DeviceClick(orUnknown(r.getDevice()), r.getCnt()))
            .toList();

    List<LinkStats.OsClick> os =
        clickRepository.findOsClicks(linkId).stream()
            .map(r -> new LinkStats.OsClick(orUnknown(r.getOs()), r.getCnt()))
            .toList();

    List<LinkStats.BrowserClick> browsers =
        clickRepository.findBrowserClicks(linkId).stream()
            .map(r -> new LinkStats.BrowserClick(orUnknown(r.getBrowser()), r.getCnt()))
            .toList();

    List<LinkStats.UtmCampaignClick> campaigns =
        clickRepository.findUtmCampaignClicks(linkId).stream()
            .map(r -> new LinkStats.UtmCampaignClick(r.getCampaign(), r.getCnt()))
            .toList();

    return new LinkStats(
        shortCode, total, human, bot, daily, hourly, dayOfWeek, referrers, channels, devices, os,
        browsers, campaigns);
  }

  private static String mapDayOfWeek(int mysqlDow) {
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
