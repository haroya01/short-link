package com.example.short_link.link.application;

import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.domain.UserRepository;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
  private static final Duration BASELINE_WINDOW = Duration.ofHours(24);
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
  private static final Pageable TOP_50 = PageRequest.ofSize(50);
  private static final int LIFECYCLE_MAX_DAY = 30;

  private final LinkRepository linkRepository;
  private final ClickEventRepository clickRepository;
  private final UserRepository userRepository;
  private final ReferrerChannelClassifier channelClassifier;
  private final LinkInsights insightsCalculator;
  private final com.example.short_link.link.domain.LinkDestinationRepository destinationRepository;
  private final LinkAccessGuard accessGuard;

  @Transactional(readOnly = true)
  public LinkStats stats(Long userId, String shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    accessGuard.requireView(userId, link);
    // Always render in the link owner's timezone so admins see the same buckets the owner does.
    Long zoneOwnerId = link.isOwnedBy(userId) ? userId : link.getUserId();
    return computeStats(link, ownerZone(zoneOwnerId));
  }

  @Transactional(readOnly = true)
  public LinkStats publicStats(String shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isStatsPublic()) {
      throw new LinkNotFoundException(shortCode);
    }
    return computeStats(link, ownerZone(link.getUserId()));
  }

  private LinkStats computeStats(LinkEntity link, ZoneId reportZone) {
    Long linkId = link.getId();
    String reportTz = currentOffset(reportZone);

    long total = clickRepository.countByLinkId(linkId);
    long human = clickRepository.countHumanByLinkId(linkId);
    long bot = clickRepository.countBotByLinkId(linkId);
    long unique = clickRepository.countUniqueVisitorsByLinkId(linkId);

    Instant firstClickAt = clickRepository.findFirstClickAt(linkId);
    Instant lastClickAt = clickRepository.findLastClickAt(linkId);
    long previewClicks = clickRepository.countPreviewByLinkId(linkId);
    Long timeToFirstClickMinutes =
        firstClickAt == null
            ? null
            : Math.max(0, Duration.between(link.getCreatedAt(), firstClickAt).toMinutes());

    Instant now = Instant.now();
    long currentHour = clickRepository.countSinceByLinkId(linkId, now.minus(Duration.ofHours(1)));
    long last24h = clickRepository.countSinceByLinkId(linkId, now.minus(BASELINE_WINDOW));
    double baseline = last24h / 24.0;
    double ratio = baseline > 0 ? currentHour / baseline : 0.0;
    LinkStats.Velocity velocity = new LinkStats.Velocity(currentHour, baseline, ratio);

    List<LinkStats.DailyClick> daily =
        clickRepository.findDailyClicks(linkId, now.minus(DAILY_WINDOW), reportTz).stream()
            .map(r -> new LinkStats.DailyClick(r.getDay(), r.getCount()))
            .toList();

    List<LinkStats.HourClick> hourly =
        clickRepository.findHourlyClicks(linkId, reportTz).stream()
            .map(r -> new LinkStats.HourClick(r.getHour(), r.getCount()))
            .toList();
    Integer peakHour =
        hourly.stream()
            .max((a, b) -> Long.compare(a.count(), b.count()))
            .map(LinkStats.HourClick::hour)
            .orElse(null);

    List<LinkStats.DayOfWeekClick> dayOfWeek =
        clickRepository.findDayOfWeekClicks(linkId, reportTz).stream()
            .map(r -> new LinkStats.DayOfWeekClick(mapDayOfWeek(r.getDow()), r.getCount()))
            .toList();

    List<LinkStats.HeatmapCell> heatmap =
        clickRepository.findHeatmap(linkId, reportTz).stream()
            .map(
                r -> new LinkStats.HeatmapCell(mapDayOfWeek(r.getDow()), r.getHour(), r.getCount()))
            .toList();

    List<LinkStats.ReferrerClick> referrers =
        clickRepository.findReferrerClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.ReferrerClick(r.getReferrer(), r.getCount()))
            .toList();

    List<LinkStats.ReferrerHostClick> referrerHosts =
        clickRepository.findReferrerHostClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.ReferrerHostClick(r.getHost(), r.getCount()))
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

    List<LinkStats.BotClick> botBreakdown =
        clickRepository.findBotClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.BotClick(orUnknown(r.getBot()), r.getCount()))
            .toList();

    List<LinkStats.UtmCampaignClick> campaigns =
        clickRepository.findUtmCampaignClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.UtmCampaignClick(r.getCampaign(), r.getCount()))
            .toList();

    List<LinkStats.UtmSourceClick> utmSources =
        clickRepository.findUtmSourceClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.UtmSourceClick(r.getSource(), r.getCount()))
            .toList();

    List<LinkStats.UtmMediumClick> utmMediums =
        clickRepository.findUtmMediumClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.UtmMediumClick(r.getMedium(), r.getCount()))
            .toList();

    List<LinkStats.UtmContentClick> utmContents =
        clickRepository.findUtmContentClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.UtmContentClick(r.getContent(), r.getCount()))
            .toList();

    List<LinkStats.SourceChannelClick> sourceChannels =
        clickRepository.findSourceChannelClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.SourceChannelClick(r.getSource(), r.getCount()))
            .toList();

    List<LinkStats.DestinationClick> destinations = computeDestinationClicks(link);

    List<LinkStats.CountryClick> countries =
        clickRepository.findCountryClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.CountryClick(orUnknown(r.getCountry()), r.getCount()))
            .toList();

    List<LinkStats.RegionClick> regions =
        clickRepository.findRegionClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.RegionClick(r.getRegion(), r.getCount()))
            .toList();

    List<LinkStats.CityClick> cities =
        clickRepository.findCityClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.CityClick(r.getCity(), r.getCount()))
            .toList();

    List<LinkStats.LanguageClick> languages =
        clickRepository.findLanguageClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.LanguageClick(r.getLanguage(), r.getCount()))
            .toList();

    List<LinkStats.AsnClick> asns =
        clickRepository.findAsnClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.AsnClick(r.getAsn(), r.getOrganization(), r.getCount()))
            .toList();
    long datacenterClicks = clickRepository.countDatacenterClicks(linkId);

    LinkStats.ReturnRate returnRate = computeReturnRate(linkId);
    LinkStats.Lifecycle lifecycle = computeLifecycle(linkId);
    List<LinkStats.Insight> insights =
        insightsCalculator.compute(
            total, bot, heatmap, channels, countries, returnRate, lifecycle, daily);

    return new LinkStats(
        link.getShortCode(),
        reportZone.getId(),
        total,
        human,
        bot,
        unique,
        previewClicks,
        firstClickAt,
        lastClickAt,
        timeToFirstClickMinutes,
        peakHour,
        velocity,
        returnRate,
        lifecycle,
        daily,
        hourly,
        dayOfWeek,
        heatmap,
        referrers,
        referrerHosts,
        channels,
        devices,
        os,
        browsers,
        botBreakdown,
        campaigns,
        utmSources,
        utmMediums,
        utmContents,
        sourceChannels,
        destinations,
        countries,
        regions,
        cities,
        languages,
        asns,
        datacenterClicks,
        insights);
  }

  /**
   * Joins click_event.destination_id with link_destination rows so the UI gets URL/label/weight
   * alongside counts. NULL destination_id (clicks served before A/B was set up, or fallback)
   * appears as a synthetic "default" row pointing at the original URL.
   */
  private List<LinkStats.DestinationClick> computeDestinationClicks(LinkEntity link) {
    var rows = clickRepository.findDestinationClicks(link.getId());
    if (rows.isEmpty()) return List.of();
    var byId =
        new java.util.HashMap<Long, com.example.short_link.link.domain.LinkDestinationEntity>();
    destinationRepository
        .findAllByLinkIdOrderByIdAsc(link.getId())
        .forEach(d -> byId.put(d.getId(), d));
    if (byId.isEmpty() && rows.size() == 1 && rows.get(0).getDestinationId() == null) {
      // No A/B set up — single row with NULL destination_id is just regular click volume.
      return List.of();
    }
    return rows.stream()
        .map(
            r -> {
              Long id = r.getDestinationId();
              if (id == null) {
                return new LinkStats.DestinationClick(
                    null, link.getOriginalUrl(), "default", 0, true, r.getCount());
              }
              var d = byId.get(id);
              if (d == null) {
                return new LinkStats.DestinationClick(
                    id, "(deleted)", null, 0, false, r.getCount());
              }
              return new LinkStats.DestinationClick(
                  d.getId(), d.getUrl(), d.getLabel(), d.getWeight(), d.isEnabled(), r.getCount());
            })
        .toList();
  }

  private LinkStats.ReturnRate computeReturnRate(Long linkId) {
    var row = clickRepository.findReturnRate(linkId);
    long newCount = row == null || row.getNewCount() == null ? 0 : row.getNewCount();
    long returningCount =
        row == null || row.getReturningCount() == null ? 0 : row.getReturningCount();
    long total = newCount + returningCount;
    double ratio = total == 0 ? 0.0 : (double) returningCount / total;
    return new LinkStats.ReturnRate(newCount, returningCount, ratio);
  }

  private LinkStats.Lifecycle computeLifecycle(Long linkId) {
    List<LinkStats.DayClick> days =
        clickRepository.findLifecycleClicks(linkId, LIFECYCLE_MAX_DAY).stream()
            .map(r -> new LinkStats.DayClick(r.getDay(), r.getCount()))
            .toList();
    Integer halfLife = halfLife(days);
    return new LinkStats.Lifecycle(days, halfLife);
  }

  static Integer halfLife(List<LinkStats.DayClick> days) {
    if (days.isEmpty()) return null;
    long total = 0;
    for (LinkStats.DayClick dc : days) total += dc.count();
    if (total == 0) return null;
    long target = (long) Math.ceil(total / 2.0);
    long cumulative = 0;
    for (LinkStats.DayClick dc : days) {
      cumulative += dc.count();
      if (cumulative >= target) return dc.day();
    }
    return null;
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

  private ZoneId ownerZone(Long userId) {
    if (userId == null) return DEFAULT_ZONE;
    return userRepository.findById(userId).map(u -> safeZone(u.getTimezone())).orElse(DEFAULT_ZONE);
  }

  static ZoneId safeZone(String tz) {
    if (tz == null || tz.isBlank()) return DEFAULT_ZONE;
    try {
      return ZoneId.of(tz);
    } catch (java.time.DateTimeException e) {
      return DEFAULT_ZONE;
    }
  }

  static String currentOffset(ZoneId zone) {
    ZoneOffset offset = zone.getRules().getOffset(Instant.now());
    return offset.getId().equals("Z") ? "+00:00" : offset.getId();
  }
}
