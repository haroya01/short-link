package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.classifier.application.ReferrerChannelClassifier;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.stats.application.LinkInsights;
import com.example.short_link.link.stats.domain.repository.ClickEventReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.DateTimeException;
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

/**
 * Click-stream aggregation for a single link. Two entry points — owner/admin {@link #stats} and
 * public {@link #publicStats} when {@code stats_public} is on. Both go through the same compute
 * pipeline (timezone-aware aggregations from {@link ClickEventRepository} projections) so the
 * public view is exactly what the owner sees.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LinkStatsQueryService {

  private static final Duration DAILY_WINDOW = Duration.ofDays(30);
  private static final Duration BASELINE_WINDOW = Duration.ofHours(24);
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
  private static final Pageable TOP_50 = PageRequest.ofSize(50);
  private static final int LIFECYCLE_MAX_DAY = 30;

  private final LinkRepository linkRepository;
  private final ClickEventRepository clickRepository;
  private final ClickEventReadRepository clickReadRepository;
  private final UserRepository userRepository;
  private final ReferrerChannelClassifier channelClassifier;
  private final LinkInsights insightsCalculator;
  private final LinkDestinationRepository destinationRepository;
  private final LinkAccessGuard accessGuard;

  public LinkStats stats(Long userId, String shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    accessGuard.requireView(userId, link);
    Long zoneOwnerId = link.isOwnedBy(userId) ? userId : link.getUserId();
    return computeStats(link, ownerZone(zoneOwnerId));
  }

  public LinkStats publicStats(String shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (!link.isStatsPublic()) {
      throw new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode);
    }
    return computeStats(link, ownerZone(link.getUserId()));
  }

  private LinkStats computeStats(LinkEntity link, ZoneId reportZone) {
    Long linkId = link.getId();
    String reportTz = currentOffset(reportZone);

    Totals totals = computeTotals(linkId, link.getCreatedAt());
    LinkStats.Velocity velocity = computeVelocity(linkId);
    TimeBuckets time = computeTimeBuckets(linkId, reportTz);
    ChannelBreakdowns channels = computeChannelBreakdowns(linkId);
    DeviceBreakdowns devices = computeDeviceBreakdowns(linkId);
    UtmBreakdowns utm = computeUtmBreakdowns(linkId);
    GeoBreakdowns geo = computeGeoBreakdowns(linkId);
    List<LinkStats.DestinationClick> destinations = computeDestinationClicks(link);
    LinkStats.ReturnRate returnRate = computeReturnRate(linkId);
    LinkStats.Lifecycle lifecycle = computeLifecycle(linkId);

    List<LinkStats.Insight> insights =
        insightsCalculator.compute(
            totals.total,
            totals.bot,
            time.heatmap,
            channels.channels,
            geo.countries,
            returnRate,
            lifecycle,
            time.daily);

    return new LinkStats(
        link.getShortCode(),
        reportZone.getId(),
        totals.total,
        totals.human,
        totals.bot,
        totals.unique,
        totals.previewClicks,
        totals.profileClicks,
        totals.firstClickAt,
        totals.lastClickAt,
        totals.timeToFirstClickMinutes,
        time.peakHour,
        velocity,
        returnRate,
        lifecycle,
        time.daily,
        time.hourly,
        time.dayOfWeek,
        time.heatmap,
        channels.referrers,
        channels.referrerHosts,
        channels.channels,
        devices.devices,
        devices.os,
        devices.browsers,
        devices.botBreakdown,
        utm.campaigns,
        utm.sources,
        utm.mediums,
        utm.contents,
        utm.sourceChannels,
        destinations,
        geo.countries,
        geo.regions,
        geo.cities,
        geo.languages,
        geo.asns,
        geo.datacenterClicks,
        insights);
  }

  private Totals computeTotals(Long linkId, Instant linkCreatedAt) {
    long total = clickRepository.countByLinkId(linkId);
    long human = clickReadRepository.countHumanByLinkId(linkId);
    long bot = clickReadRepository.countBotByLinkId(linkId);
    long unique = clickReadRepository.countUniqueVisitorsByLinkId(linkId);
    Instant firstClickAt = clickReadRepository.findFirstClickAt(linkId);
    Instant lastClickAt = clickReadRepository.findLastClickAt(linkId);
    long previewClicks = clickReadRepository.countPreviewByLinkId(linkId);
    long profileClicks = clickReadRepository.countProfileChannelByLinkId(linkId);
    Long timeToFirstClickMinutes =
        firstClickAt == null
            ? null
            : Math.max(0, Duration.between(linkCreatedAt, firstClickAt).toMinutes());
    return new Totals(
        total,
        human,
        bot,
        unique,
        previewClicks,
        profileClicks,
        firstClickAt,
        lastClickAt,
        timeToFirstClickMinutes);
  }

  private LinkStats.Velocity computeVelocity(Long linkId) {
    Instant now = Instant.now();
    long currentHour =
        clickReadRepository.countSinceByLinkId(linkId, now.minus(Duration.ofHours(1)));
    long last24h = clickReadRepository.countSinceByLinkId(linkId, now.minus(BASELINE_WINDOW));
    double baseline = last24h / 24.0;
    double ratio = baseline > 0 ? currentHour / baseline : 0.0;
    return new LinkStats.Velocity(currentHour, baseline, ratio);
  }

  private TimeBuckets computeTimeBuckets(Long linkId, String reportTz) {
    List<LinkStats.DailyClick> daily =
        clickReadRepository
            .findDailyClicks(linkId, Instant.now().minus(DAILY_WINDOW), reportTz)
            .stream()
            .map(r -> new LinkStats.DailyClick(r.getDay(), r.getCount()))
            .toList();
    List<LinkStats.HourClick> hourly =
        clickReadRepository.findHourlyClicks(linkId, reportTz).stream()
            .map(r -> new LinkStats.HourClick(r.getHour(), r.getCount()))
            .toList();
    Integer peakHour =
        hourly.stream()
            .max((a, b) -> Long.compare(a.count(), b.count()))
            .map(LinkStats.HourClick::hour)
            .orElse(null);
    List<LinkStats.DayOfWeekClick> dayOfWeek =
        clickReadRepository.findDayOfWeekClicks(linkId, reportTz).stream()
            .map(r -> new LinkStats.DayOfWeekClick(mapDayOfWeek(r.getDow()), r.getCount()))
            .toList();
    List<LinkStats.HeatmapCell> heatmap =
        clickReadRepository.findHeatmap(linkId, reportTz).stream()
            .map(
                r -> new LinkStats.HeatmapCell(mapDayOfWeek(r.getDow()), r.getHour(), r.getCount()))
            .toList();
    return new TimeBuckets(daily, hourly, peakHour, dayOfWeek, heatmap);
  }

  private ChannelBreakdowns computeChannelBreakdowns(Long linkId) {
    List<LinkStats.ReferrerClick> referrers =
        clickReadRepository.findReferrerClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.ReferrerClick(r.getReferrer(), r.getCount()))
            .toList();
    List<LinkStats.ReferrerHostClick> referrerHosts =
        clickReadRepository.findReferrerHostClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.ReferrerHostClick(r.getHost(), r.getCount()))
            .toList();
    Map<String, Long> channelTotals = new HashMap<>();
    referrers.forEach(
        r -> channelTotals.merge(channelClassifier.classify(r.referrer()), r.count(), Long::sum));
    long direct = clickReadRepository.countDirectByLinkId(linkId);
    if (direct > 0) channelTotals.merge("direct", direct, Long::sum);
    List<LinkStats.ChannelClick> channels =
        channelTotals.entrySet().stream()
            .map(e -> new LinkStats.ChannelClick(e.getKey(), e.getValue()))
            .sorted((a, b) -> Long.compare(b.count(), a.count()))
            .toList();
    return new ChannelBreakdowns(referrers, referrerHosts, channels);
  }

  private DeviceBreakdowns computeDeviceBreakdowns(Long linkId) {
    List<LinkStats.DeviceClick> devices =
        clickReadRepository.findDeviceClicks(linkId).stream()
            .map(r -> new LinkStats.DeviceClick(orUnknown(r.getDevice()), r.getCount()))
            .toList();
    List<LinkStats.OsClick> os =
        clickReadRepository.findOsClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.OsClick(orUnknown(r.getOs()), r.getCount()))
            .toList();
    List<LinkStats.BrowserClick> browsers =
        clickReadRepository.findBrowserClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.BrowserClick(orUnknown(r.getBrowser()), r.getCount()))
            .toList();
    List<LinkStats.BotClick> botBreakdown =
        clickReadRepository.findBotClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.BotClick(orUnknown(r.getBot()), r.getCount()))
            .toList();
    return new DeviceBreakdowns(devices, os, browsers, botBreakdown);
  }

  private UtmBreakdowns computeUtmBreakdowns(Long linkId) {
    List<LinkStats.UtmCampaignClick> campaigns =
        clickReadRepository.findUtmCampaignClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.UtmCampaignClick(r.getCampaign(), r.getCount()))
            .toList();
    List<LinkStats.UtmSourceClick> sources =
        clickReadRepository.findUtmSourceClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.UtmSourceClick(r.getSource(), r.getCount()))
            .toList();
    List<LinkStats.UtmMediumClick> mediums =
        clickReadRepository.findUtmMediumClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.UtmMediumClick(r.getMedium(), r.getCount()))
            .toList();
    List<LinkStats.UtmContentClick> contents =
        clickReadRepository.findUtmContentClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.UtmContentClick(r.getContent(), r.getCount()))
            .toList();
    List<LinkStats.SourceChannelClick> sourceChannels =
        clickReadRepository.findSourceChannelClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.SourceChannelClick(r.getSource(), r.getCount()))
            .toList();
    return new UtmBreakdowns(campaigns, sources, mediums, contents, sourceChannels);
  }

  private GeoBreakdowns computeGeoBreakdowns(Long linkId) {
    List<LinkStats.CountryClick> countries =
        clickReadRepository.findCountryClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.CountryClick(orUnknown(r.getCountry()), r.getCount()))
            .toList();
    List<LinkStats.RegionClick> regions =
        clickReadRepository.findRegionClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.RegionClick(r.getRegion(), r.getCount()))
            .toList();
    List<LinkStats.CityClick> cities =
        clickReadRepository.findCityClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.CityClick(r.getCity(), r.getCount()))
            .toList();
    List<LinkStats.LanguageClick> languages =
        clickReadRepository.findLanguageClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.LanguageClick(r.getLanguage(), r.getCount()))
            .toList();
    List<LinkStats.AsnClick> asns =
        clickReadRepository.findAsnClicks(linkId, TOP_50).stream()
            .map(r -> new LinkStats.AsnClick(r.getAsn(), r.getOrganization(), r.getCount()))
            .toList();
    long datacenterClicks = clickReadRepository.countDatacenterClicks(linkId);
    return new GeoBreakdowns(countries, regions, cities, languages, asns, datacenterClicks);
  }

  private List<LinkStats.DestinationClick> computeDestinationClicks(LinkEntity link) {
    var rows = clickReadRepository.findDestinationClicks(link.getId());
    if (rows.isEmpty()) return List.of();
    var byId = new HashMap<Long, LinkDestinationEntity>();
    destinationRepository
        .findAllByLinkIdOrderByIdAsc(link.getId())
        .forEach(d -> byId.put(d.getId(), d));
    if (byId.isEmpty() && rows.size() == 1 && rows.get(0).getDestinationId() == null) {
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
    var row = clickReadRepository.findReturnRate(linkId);
    long newCount = row == null || row.getNewCount() == null ? 0 : row.getNewCount();
    long returningCount =
        row == null || row.getReturningCount() == null ? 0 : row.getReturningCount();
    long total = newCount + returningCount;
    double ratio = total == 0 ? 0.0 : (double) returningCount / total;
    return new LinkStats.ReturnRate(newCount, returningCount, ratio);
  }

  private LinkStats.Lifecycle computeLifecycle(Long linkId) {
    List<LinkStats.DayClick> days =
        clickReadRepository.findLifecycleClicks(linkId, LIFECYCLE_MAX_DAY).stream()
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

  public static ZoneId safeZone(String tz) {
    if (tz == null || tz.isBlank()) return DEFAULT_ZONE;
    try {
      return ZoneId.of(tz);
    } catch (DateTimeException e) {
      return DEFAULT_ZONE;
    }
  }

  public static String currentOffset(ZoneId zone) {
    ZoneOffset offset = zone.getRules().getOffset(Instant.now());
    return offset.getId().equals("Z") ? "+00:00" : offset.getId();
  }

  private record Totals(
      long total,
      long human,
      long bot,
      long unique,
      long previewClicks,
      long profileClicks,
      Instant firstClickAt,
      Instant lastClickAt,
      Long timeToFirstClickMinutes) {}

  private record TimeBuckets(
      List<LinkStats.DailyClick> daily,
      List<LinkStats.HourClick> hourly,
      Integer peakHour,
      List<LinkStats.DayOfWeekClick> dayOfWeek,
      List<LinkStats.HeatmapCell> heatmap) {}

  private record ChannelBreakdowns(
      List<LinkStats.ReferrerClick> referrers,
      List<LinkStats.ReferrerHostClick> referrerHosts,
      List<LinkStats.ChannelClick> channels) {}

  private record DeviceBreakdowns(
      List<LinkStats.DeviceClick> devices,
      List<LinkStats.OsClick> os,
      List<LinkStats.BrowserClick> browsers,
      List<LinkStats.BotClick> botBreakdown) {}

  private record UtmBreakdowns(
      List<LinkStats.UtmCampaignClick> campaigns,
      List<LinkStats.UtmSourceClick> sources,
      List<LinkStats.UtmMediumClick> mediums,
      List<LinkStats.UtmContentClick> contents,
      List<LinkStats.SourceChannelClick> sourceChannels) {}

  private record GeoBreakdowns(
      List<LinkStats.CountryClick> countries,
      List<LinkStats.RegionClick> regions,
      List<LinkStats.CityClick> cities,
      List<LinkStats.LanguageClick> languages,
      List<LinkStats.AsnClick> asns,
      long datacenterClicks) {}
}
