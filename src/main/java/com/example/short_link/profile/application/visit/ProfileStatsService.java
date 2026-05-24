package com.example.short_link.profile.application.visit;

import com.example.short_link.profile.domain.visit.ProfileVisitRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserNotFoundException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
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
  private final ProfileVisitRepository visits;

  @Transactional(readOnly = true)
  public ProfileStats statsForOwner(Long ownerUserId) {
    UserEntity owner = userRepository.findById(ownerUserId).orElseThrow(UserNotFoundException::new);
    return computeStats(owner);
  }

  /**
   * Public stats lookup by username. Throws {@link UserNotFoundException} when the user doesn't
   * exist OR when they haven't opted into public stats — collapsing the two into the same 404 so
   * the existence of a profile that's opted out isn't leaked.
   */
  @Transactional(readOnly = true)
  public ProfileStats publicStats(String username) {
    UserEntity user =
        userRepository.findByUsername(username).orElseThrow(UserNotFoundException::new);
    if (!user.isStatsPublic()) {
      throw new UserNotFoundException();
    }
    return computeStats(user);
  }

  @Transactional(readOnly = true)
  public boolean statsPublic(Long ownerUserId) {
    UserEntity owner = userRepository.findById(ownerUserId).orElseThrow(UserNotFoundException::new);
    return owner.isStatsPublic();
  }

  @Transactional
  public boolean updateStatsPublic(Long ownerUserId, boolean statsPublic) {
    UserEntity owner = userRepository.findById(ownerUserId).orElseThrow(UserNotFoundException::new);
    owner.updateStatsPublic(statsPublic);
    userRepository.save(owner);
    return owner.isStatsPublic();
  }

  @Transactional(readOnly = true)
  public ProfileVisitSummary summaryForOwner(Long ownerUserId) {
    Instant now = Instant.now();
    long allTime = visits.countByProfileUserIdAndBotFalse(ownerUserId);
    long today =
        visits.countByProfileUserIdAndBotFalseAndVisitedAtAfter(
            ownerUserId, now.minus(Duration.ofDays(1)));
    long week =
        visits.countByProfileUserIdAndBotFalseAndVisitedAtAfter(
            ownerUserId, now.minus(Duration.ofDays(7)));
    long month =
        visits.countByProfileUserIdAndBotFalseAndVisitedAtAfter(
            ownerUserId, now.minus(Duration.ofDays(30)));
    return new ProfileVisitSummary(today, week, month, allTime);
  }

  private ProfileStats computeStats(UserEntity owner) {
    Long uid = owner.getId();
    ZoneId zone = safeZone(owner.getTimezone());
    String tz = currentOffset(zone);

    long total = visits.countByProfileUserId(uid);
    long human = visits.countByProfileUserIdAndBotFalse(uid);
    long bot = visits.countBotByProfileUserId(uid);
    long unique = visits.countUniqueVisitorsByProfileUserId(uid);

    Instant firstAt = visits.findFirstVisitAt(uid);
    Instant lastAt = visits.findLastVisitAt(uid);

    Instant now = Instant.now();
    List<ProfileStats.DailyVisit> daily =
        visits.findDailyVisits(uid, now.minus(DAILY_WINDOW), tz).stream()
            .map(r -> new ProfileStats.DailyVisit(r.getDay(), r.getCount()))
            .toList();

    List<ProfileStats.HourVisit> hourly =
        visits.findHourlyVisits(uid, tz).stream()
            .map(r -> new ProfileStats.HourVisit(r.getHour(), r.getCount()))
            .toList();
    Integer peakHour =
        hourly.stream()
            .max((a, b) -> Long.compare(a.count(), b.count()))
            .map(ProfileStats.HourVisit::hour)
            .orElse(null);

    List<ProfileStats.HeatmapCell> heatmap =
        visits.findHeatmap(uid, tz).stream()
            .map(
                r ->
                    new ProfileStats.HeatmapCell(
                        dayOfWeekName(r.getDow()), r.getHour(), r.getCount()))
            .toList();

    List<ProfileStats.CountryVisit> countries =
        visits.findCountryVisits(uid, TOP_50).stream()
            .map(r -> new ProfileStats.CountryVisit(orUnknown(r.getCountry()), r.getCount()))
            .toList();

    List<ProfileStats.DeviceVisit> devices =
        visits.findDeviceVisits(uid).stream()
            .map(r -> new ProfileStats.DeviceVisit(orUnknown(r.getDevice()), r.getCount()))
            .toList();

    List<ProfileStats.BrowserVisit> browsers =
        visits.findBrowserVisits(uid, TOP_50).stream()
            .map(r -> new ProfileStats.BrowserVisit(orUnknown(r.getBrowser()), r.getCount()))
            .toList();

    List<ProfileStats.ReferrerHostVisit> referrerHosts =
        visits.findReferrerHostVisits(uid, TOP_50).stream()
            .map(r -> new ProfileStats.ReferrerHostVisit(r.getHost(), r.getCount()))
            .toList();

    List<ProfileStats.SourceChannelVisit> channels =
        visits.findSourceChannelVisits(uid, TOP_50).stream()
            .map(r -> new ProfileStats.SourceChannelVisit(r.getSource(), r.getCount()))
            .toList();

    List<ProfileStats.UtmCampaignVisit> utmCampaigns =
        visits.findUtmCampaignVisits(uid, TOP_50).stream()
            .map(r -> new ProfileStats.UtmCampaignVisit(r.getCampaign(), r.getCount()))
            .toList();

    List<ProfileStats.UtmSourceVisit> utmSources =
        visits.findUtmSourceVisits(uid, TOP_50).stream()
            .map(r -> new ProfileStats.UtmSourceVisit(r.getSource(), r.getCount()))
            .toList();

    return new ProfileStats(
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

  /** MySQL's DAYOFWEEK returns 1=Sunday..7=Saturday; map to the same labels LinkStats uses. */
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
