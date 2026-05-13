package com.example.short_link.profile.visit;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
public class ProfileStatsService {

  private static final Duration DAILY_WINDOW = Duration.ofDays(30);
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
  private static final Pageable TOP_50 = PageRequest.ofSize(50);

  private final UserRepository userRepository;
  private final ProfileVisitRepository visitRepository;

  @Transactional(readOnly = true)
  public ProfileStats stats(Long userId) {
    UserEntity owner = userRepository.findById(userId).orElseThrow();
    ZoneId reportZone = ownerZone(owner);
    String tz = currentOffset(reportZone);

    long total = visitRepository.countByProfileUserId(userId);
    long human = visitRepository.countByProfileUserIdAndBotFalse(userId);
    long bot = visitRepository.countBotByUserId(userId);
    long unique = visitRepository.countUniqueVisitorsByUserId(userId);

    Instant firstVisitAt = visitRepository.findFirstVisitAt(userId);
    Instant lastVisitAt = visitRepository.findLastVisitAt(userId);

    Instant dailyFrom = Instant.now().minus(DAILY_WINDOW);
    List<ProfileStats.DailyVisit> daily =
        visitRepository.findDailyVisits(userId, dailyFrom, tz).stream()
            .map(r -> new ProfileStats.DailyVisit(r.getDay(), r.getCount()))
            .toList();

    List<ProfileStats.HourVisit> hourly = new ArrayList<>();
    Integer peakHour = null;
    long peakCount = -1;
    for (ProfileVisitRepository.HourVisitRow row : visitRepository.findHourlyVisits(userId, tz)) {
      hourly.add(new ProfileStats.HourVisit(row.getHour(), row.getCount()));
      if (row.getCount() > peakCount) {
        peakCount = row.getCount();
        peakHour = row.getHour();
      }
    }

    List<ProfileStats.HeatmapCell> heatmap =
        visitRepository.findHeatmap(userId, tz).stream()
            .map(
                r ->
                    new ProfileStats.HeatmapCell(
                        dayOfWeekName(r.getDow()), r.getHour(), r.getCount()))
            .toList();

    List<ProfileStats.CountryVisit> countries =
        visitRepository.findCountryVisits(userId, TOP_50).stream()
            .map(r -> new ProfileStats.CountryVisit(r.getCountry(), r.getCount()))
            .toList();

    List<ProfileStats.DeviceVisit> devices =
        visitRepository.findDeviceVisits(userId).stream()
            .map(r -> new ProfileStats.DeviceVisit(r.getDevice(), r.getCount()))
            .toList();

    List<ProfileStats.BrowserVisit> browsers =
        visitRepository.findBrowserVisits(userId, TOP_50).stream()
            .map(r -> new ProfileStats.BrowserVisit(r.getBrowser(), r.getCount()))
            .toList();

    List<ProfileStats.ReferrerHostVisit> referrerHosts =
        visitRepository.findReferrerHostVisits(userId, TOP_50).stream()
            .map(r -> new ProfileStats.ReferrerHostVisit(r.getHost(), r.getCount()))
            .toList();

    List<ProfileStats.SourceChannelVisit> sources =
        visitRepository.findSourceChannelVisits(userId, TOP_50).stream()
            .map(r -> new ProfileStats.SourceChannelVisit(r.getSource(), r.getCount()))
            .toList();

    List<ProfileStats.UtmCampaignVisit> utmCampaigns =
        visitRepository.findUtmCampaignVisits(userId, TOP_50).stream()
            .map(r -> new ProfileStats.UtmCampaignVisit(r.getCampaign(), r.getCount()))
            .toList();

    List<ProfileStats.UtmSourceVisit> utmSources =
        visitRepository.findUtmSourceVisits(userId, TOP_50).stream()
            .map(r -> new ProfileStats.UtmSourceVisit(r.getSource(), r.getCount()))
            .toList();

    return new ProfileStats(
        tz,
        total,
        human,
        bot,
        unique,
        firstVisitAt,
        lastVisitAt,
        peakHour,
        daily,
        hourly,
        heatmap,
        countries,
        devices,
        browsers,
        referrerHosts,
        sources,
        utmCampaigns,
        utmSources);
  }

  /** Summary card for /profile/edit — just totals over a few time windows. */
  @Transactional(readOnly = true)
  public ProfileVisitSummary summary(Long userId) {
    Instant now = Instant.now();
    long today =
        visitRepository.countByProfileUserIdAndBotFalseAndVisitedAtAfter(
            userId, now.minus(Duration.ofDays(1)));
    long week =
        visitRepository.countByProfileUserIdAndBotFalseAndVisitedAtAfter(
            userId, now.minus(Duration.ofDays(7)));
    long month =
        visitRepository.countByProfileUserIdAndBotFalseAndVisitedAtAfter(
            userId, now.minus(Duration.ofDays(30)));
    long allTime = visitRepository.countByProfileUserIdAndBotFalse(userId);
    return new ProfileVisitSummary(today, week, month, allTime);
  }

  public record ProfileVisitSummary(long today, long week, long month, long allTime) {}

  private ZoneId ownerZone(UserEntity user) {
    String tz = user.getTimezone();
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

  private static final Map<Integer, String> DOW_NAMES = new HashMap<>();

  static {
    // MySQL DAYOFWEEK: 1=Sunday..7=Saturday
    DOW_NAMES.put(1, "SUNDAY");
    DOW_NAMES.put(2, "MONDAY");
    DOW_NAMES.put(3, "TUESDAY");
    DOW_NAMES.put(4, "WEDNESDAY");
    DOW_NAMES.put(5, "THURSDAY");
    DOW_NAMES.put(6, "FRIDAY");
    DOW_NAMES.put(7, "SATURDAY");
  }

  private static String dayOfWeekName(Integer dow) {
    if (dow == null) return DayOfWeek.MONDAY.name();
    return DOW_NAMES.getOrDefault(dow, DayOfWeek.MONDAY.name());
  }
}
