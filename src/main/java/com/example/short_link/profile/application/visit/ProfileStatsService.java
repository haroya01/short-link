package com.example.short_link.profile.application.visit;

import static com.example.short_link.common.analytics.VisitStatsAssembler.DAILY_WINDOW;
import static com.example.short_link.common.analytics.VisitStatsAssembler.TOP_SIZE;
import static com.example.short_link.common.analytics.VisitStatsAssembler.overPeriods;

import com.example.short_link.common.analytics.VisitStats;
import com.example.short_link.common.analytics.VisitStatsAssembler;
import com.example.short_link.common.time.OffsetPeriod;
import com.example.short_link.common.time.ReportZones;
import com.example.short_link.profile.domain.repository.ProfileVisitRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileStatsService {

  private final UserRepository userRepository;
  private final ProfileVisitRepository visits;
  private final Clock clock;

  @Transactional(readOnly = true)
  public VisitStats statsForOwner(Long ownerUserId) {
    UserEntity owner =
        userRepository
            .findById(ownerUserId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    return computeStats(owner);
  }

  @Transactional(readOnly = true)
  public VisitStats publicStats(String username) {
    UserEntity user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    if (!user.isStatsPublic()) {
      throw new UserException(UserErrorCode.USER_NOT_FOUND);
    }
    return computeStats(user);
  }

  @Transactional(readOnly = true)
  public boolean statsPublic(Long ownerUserId) {
    UserEntity owner =
        userRepository
            .findById(ownerUserId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    return owner.isStatsPublic();
  }

  @Transactional(readOnly = true)
  public ProfileVisitSummary summaryForOwner(Long ownerUserId) {
    Instant now = clock.instant();
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

  private VisitStats computeStats(UserEntity owner) {
    Long uid = owner.getId();
    ZoneId zone = ReportZones.zoneOf(owner.getTimezone());
    Instant now = clock.instant();
    Instant until = now.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);

    VisitStatsAssembler.Totals totals =
        new VisitStatsAssembler.Totals(
            visits.countByProfileUserId(uid),
            visits.countByProfileUserIdAndBotFalse(uid),
            visits.countBotByProfileUserId(uid),
            visits.countUniqueVisitorsByProfileUserId(uid),
            visits.findFirstVisitAt(uid),
            visits.findLastVisitAt(uid));
    List<OffsetPeriod> recent = OffsetPeriod.between(zone, now.minus(DAILY_WINDOW), until);
    List<OffsetPeriod> lifetime = OffsetPeriod.between(zone, totals.firstVisitOr(now), until);
    VisitStatsAssembler.Rows rows =
        new VisitStatsAssembler.Rows(
            overPeriods(recent, p -> visits.findDailyVisits(uid, p.from(), p.until(), p.offset())),
            overPeriods(
                lifetime, p -> visits.findHourlyVisits(uid, p.from(), p.until(), p.offset())),
            overPeriods(lifetime, p -> visits.findHeatmap(uid, p.from(), p.until(), p.offset())),
            visits.topCountries(uid, TOP_SIZE),
            visits.findDeviceVisits(uid),
            visits.topBrowsers(uid, TOP_SIZE),
            visits.topReferrerHosts(uid, TOP_SIZE),
            visits.topSourceChannels(uid, TOP_SIZE),
            visits.topUtmCampaigns(uid, TOP_SIZE),
            visits.topUtmSources(uid, TOP_SIZE));
    return VisitStatsAssembler.assemble(zone, totals, rows);
  }
}
