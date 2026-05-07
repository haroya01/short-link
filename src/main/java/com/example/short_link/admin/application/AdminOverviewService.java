package com.example.short_link.admin.application;

import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.domain.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOverviewService {

  private static final Pageable TOP_10 = PageRequest.ofSize(10);
  private static final Duration WINDOW_30D = Duration.ofDays(30);
  private static final Duration WINDOW_7D = Duration.ofDays(7);
  private static final String REPORT_TZ = "+09:00";

  private final UserRepository userRepository;
  private final LinkRepository linkRepository;
  private final AdminMetricsRepository metricsRepository;

  @Cacheable(value = "admin-overview", key = "'overview'")
  @Transactional(readOnly = true)
  public AdminOverview overview() {
    Instant now = Instant.now();
    Instant from30 = now.minus(WINDOW_30D);
    Instant from7 = now.minus(WINDOW_7D);

    long totalUsers = userRepository.count();
    long totalLinks = linkRepository.count();
    long totalClicks = metricsRepository.totalClicks();

    long newUsers7d = userRepository.countByCreatedAtAfter(from7);
    long newLinks7d = linkRepository.countByCreatedAtAfter(from7);
    long clicks7d = metricsRepository.clicksSince(from7);

    long anonymousLinks = linkRepository.countByUserIdIsNull();
    long expiredLinks = linkRepository.countByExpiresAtBefore(now);
    long clicklessLinks = metricsRepository.linksWithoutClicks();

    double anonymousRatio = totalLinks == 0 ? 0 : (double) anonymousLinks / totalLinks;
    double expiredRatio = totalLinks == 0 ? 0 : (double) expiredLinks / totalLinks;
    double clicklessRatio = totalLinks == 0 ? 0 : (double) clicklessLinks / totalLinks;

    List<AdminOverview.DailyPoint> signups =
        metricsRepository.dailySignupsSince(from30, REPORT_TZ).stream()
            .map(r -> new AdminOverview.DailyPoint(r.getDay(), r.getCount()))
            .toList();
    List<AdminOverview.DailyPoint> links =
        metricsRepository.dailyLinksSince(from30, REPORT_TZ).stream()
            .map(r -> new AdminOverview.DailyPoint(r.getDay(), r.getCount()))
            .toList();
    List<AdminOverview.DailyPoint> clicks =
        metricsRepository.dailyClicksSince(from30, REPORT_TZ).stream()
            .map(r -> new AdminOverview.DailyPoint(r.getDay(), r.getCount()))
            .toList();

    List<AdminOverview.UserStat> topByLinks =
        metricsRepository.topUsersByLinks(TOP_10).stream()
            .map(r -> new AdminOverview.UserStat(r.getUserId(), r.getEmail(), r.getCount()))
            .toList();
    List<AdminOverview.UserStat> topByClicks =
        metricsRepository.topUsersByClicks(TOP_10).stream()
            .map(r -> new AdminOverview.UserStat(r.getUserId(), r.getEmail(), r.getCount()))
            .toList();
    List<AdminOverview.LinkStat> topLinks =
        metricsRepository.topLinksByClicks(TOP_10).stream()
            .map(r -> new AdminOverview.LinkStat(r.getShortCode(), r.getCount(), r.getOwnerEmail()))
            .toList();

    return new AdminOverview(
        new AdminOverview.Totals(totalUsers, totalLinks, totalClicks),
        newUsers7d,
        newLinks7d,
        clicks7d,
        anonymousRatio,
        expiredRatio,
        clicklessRatio,
        signups,
        links,
        clicks,
        topByLinks,
        topByClicks,
        topLinks);
  }
}
