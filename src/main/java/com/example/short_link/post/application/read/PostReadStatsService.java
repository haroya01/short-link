package com.example.short_link.post.application.read;

import com.example.short_link.common.analytics.HeatmapCell;
import com.example.short_link.common.analytics.VisitStats;
import com.example.short_link.post.application.write.SeriesOwnership;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostReadStatsReader;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostReadStatsService {

  private static final Duration DAILY_WINDOW = Duration.ofDays(30);
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
  private static final int TOP_SIZE = 50;

  private final PostReadStatsReader reader;
  private final PostRepository postRepository;
  private final SeriesOwnership seriesOwnership;
  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public VisitStats forPost(Long userId, Long postId) {
    PostEntity post =
        postRepository
            .findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, postId));
    if (!post.isOwnedBy(userId)) {
      throw new PostException(PostErrorCode.PERMISSION_DENIED).with("postId", postId);
    }
    return compute(List.of(postId), userId);
  }

  @Transactional(readOnly = true)
  public VisitStats forSeries(Long userId, Long seriesId) {
    seriesOwnership.requireOwned(userId, seriesId);
    List<Long> postIds =
        postRepository.findAllBySeriesIdOrderBySeriesOrderAsc(seriesId).stream()
            .map(PostEntity::getId)
            .toList();
    return compute(postIds, userId);
  }

  private VisitStats compute(Collection<Long> postIds, Long ownerUserId) {
    ZoneId zone = safeZone(ownerTimezone(ownerUserId));
    if (postIds.isEmpty()) {
      // An empty series must short-circuit before SQL IN ().
      return VisitStats.empty(zone.getId());
    }
    String tz = currentOffset(zone);

    long total = reader.countViews(postIds);
    long human = reader.countHuman(postIds);
    long bot = reader.countBot(postIds);
    long unique = reader.countUnique(postIds);
    Instant firstAt = reader.firstViewedAt(postIds);
    Instant lastAt = reader.lastViewedAt(postIds);

    Instant now = Instant.now();
    List<VisitStats.DailyVisit> daily =
        reader.daily(postIds, now.minus(DAILY_WINDOW), tz).stream()
            .map(r -> new VisitStats.DailyVisit(r.getDay(), r.getCount()))
            .toList();

    List<VisitStats.HourVisit> hourly =
        reader.hourly(postIds, tz).stream()
            .map(r -> new VisitStats.HourVisit(r.getHour(), r.getCount()))
            .toList();
    Integer peakHour =
        hourly.stream()
            .max((a, b) -> Long.compare(a.count(), b.count()))
            .map(VisitStats.HourVisit::hour)
            .orElse(null);

    List<HeatmapCell> heatmap =
        reader.heatmap(postIds, tz).stream()
            .map(r -> new HeatmapCell(dayOfWeekName(r.getDow()), r.getHour(), r.getCount()))
            .toList();

    List<VisitStats.CountryVisit> countries =
        reader.topCountries(postIds, TOP_SIZE).stream()
            .map(r -> new VisitStats.CountryVisit(orUnknown(r.getCountry()), r.getCount()))
            .toList();

    List<VisitStats.DeviceVisit> devices =
        reader.topDevices(postIds, TOP_SIZE).stream()
            .map(r -> new VisitStats.DeviceVisit(orUnknown(r.getDevice()), r.getCount()))
            .toList();

    List<VisitStats.BrowserVisit> browsers =
        reader.topBrowsers(postIds, TOP_SIZE).stream()
            .map(r -> new VisitStats.BrowserVisit(orUnknown(r.getBrowser()), r.getCount()))
            .toList();

    List<VisitStats.ReferrerHostVisit> referrerHosts =
        reader.topReferrerHosts(postIds, TOP_SIZE).stream()
            .map(r -> new VisitStats.ReferrerHostVisit(r.getHost(), r.getCount()))
            .toList();

    List<VisitStats.SourceChannelVisit> channels =
        reader.topSourceChannels(postIds, TOP_SIZE).stream()
            .map(r -> new VisitStats.SourceChannelVisit(r.getSource(), r.getCount()))
            .toList();

    List<VisitStats.UtmCampaignVisit> utmCampaigns =
        reader.topUtmCampaigns(postIds, TOP_SIZE).stream()
            .map(r -> new VisitStats.UtmCampaignVisit(r.getCampaign(), r.getCount()))
            .toList();

    List<VisitStats.UtmSourceVisit> utmSources =
        reader.topUtmSources(postIds, TOP_SIZE).stream()
            .map(r -> new VisitStats.UtmSourceVisit(r.getSource(), r.getCount()))
            .toList();

    return new VisitStats(
        zone.getId(),
        total,
        human,
        bot,
        unique,
        firstAt,
        lastAt,
        peakHour,
        daily,
        hourly,
        heatmap,
        countries,
        devices,
        browsers,
        referrerHosts,
        channels,
        utmCampaigns,
        utmSources);
  }

  private String ownerTimezone(Long ownerUserId) {
    return userRepository.findById(ownerUserId).map(UserEntity::getTimezone).orElse(null);
  }

  private static String dayOfWeekName(int dow) {
    return switch (dow) {
      case 1 -> DayOfWeek.SUNDAY.name();
      case 2 -> DayOfWeek.MONDAY.name();
      case 3 -> DayOfWeek.TUESDAY.name();
      case 4 -> DayOfWeek.WEDNESDAY.name();
      case 5 -> DayOfWeek.THURSDAY.name();
      case 6 -> DayOfWeek.FRIDAY.name();
      case 7 -> DayOfWeek.SATURDAY.name();
      default -> "UNKNOWN";
    };
  }

  private static String orUnknown(String v) {
    return v == null ? "unknown" : v;
  }

  private static ZoneId safeZone(String tz) {
    if (tz == null || tz.isBlank()) return DEFAULT_ZONE;
    try {
      return ZoneId.of(tz);
    } catch (DateTimeException e) {
      return DEFAULT_ZONE;
    }
  }

  private static String currentOffset(ZoneId zone) {
    ZoneOffset offset = zone.getRules().getOffset(Instant.now());
    return offset.getId().equals("Z") ? "+00:00" : offset.getId();
  }
}
