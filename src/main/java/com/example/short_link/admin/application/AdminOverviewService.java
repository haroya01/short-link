package com.example.short_link.admin.application;

import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.user.domain.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
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

    Page<AdminMetricsRepository.UserStatRow> topByLinksPage =
        metricsRepository.topUsersByLinks(TOP_10);
    List<AdminOverview.UserStat> topByLinks =
        topByLinksPage.getContent().stream()
            .map(r -> new AdminOverview.UserStat(r.getUserId(), r.getEmail(), r.getCount()))
            .toList();
    Page<AdminMetricsRepository.UserStatRow> topByClicksPage =
        metricsRepository.topUsersByClicks(TOP_10);
    List<AdminOverview.UserStat> topByClicks =
        topByClicksPage.getContent().stream()
            .map(r -> new AdminOverview.UserStat(r.getUserId(), r.getEmail(), r.getCount()))
            .toList();
    Page<AdminMetricsRepository.LinkStatRow> topLinksPage =
        metricsRepository.topLinksByClicks(TOP_10);
    List<AdminOverview.LinkStat> topLinks =
        topLinksPage.getContent().stream()
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
        topByLinksPage.getTotalElements(),
        topByClicks,
        topByClicksPage.getTotalElements(),
        topLinks,
        topLinksPage.getTotalElements());
  }

  @Transactional(readOnly = true)
  public TopUsersPage topUsersByLinks(int page, int size) {
    Page<AdminMetricsRepository.UserStatRow> rows =
        metricsRepository.topUsersByLinks(PageRequest.of(page, clampSize(size)));
    return new TopUsersPage(
        rows.getContent().stream()
            .map(r -> new AdminOverview.UserStat(r.getUserId(), r.getEmail(), r.getCount()))
            .toList(),
        rows.getTotalElements());
  }

  @Transactional(readOnly = true)
  public TopUsersPage topUsersByClicks(int page, int size) {
    Page<AdminMetricsRepository.UserStatRow> rows =
        metricsRepository.topUsersByClicks(PageRequest.of(page, clampSize(size)));
    return new TopUsersPage(
        rows.getContent().stream()
            .map(r -> new AdminOverview.UserStat(r.getUserId(), r.getEmail(), r.getCount()))
            .toList(),
        rows.getTotalElements());
  }

  @Transactional(readOnly = true)
  public TopLinksPage topLinksByClicks(int page, int size) {
    Page<AdminMetricsRepository.LinkStatRow> rows =
        metricsRepository.topLinksByClicks(PageRequest.of(page, clampSize(size)));
    return new TopLinksPage(
        rows.getContent().stream()
            .map(r -> new AdminOverview.LinkStat(r.getShortCode(), r.getCount(), r.getOwnerEmail()))
            .toList(),
        rows.getTotalElements());
  }

  // Hard cap protects the DB from a `?size=10000` scrape that would scan the full users / links
  // table per request — the table is bounded but the per-page render budget on the admin UI is ~50.
  private static int clampSize(int requested) {
    if (requested <= 0) return 10;
    return Math.min(requested, 50);
  }

  public record TopUsersPage(List<AdminOverview.UserStat> items, long total) {}

  public record TopLinksPage(List<AdminOverview.LinkStat> items, long total) {}
}
