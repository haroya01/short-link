package com.example.short_link.link.stats.application;

import com.example.short_link.link.application.dto.WeeklyInsights;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.application.read.LinkStatsQueryService;
import com.example.short_link.link.stats.domain.repository.ClickRangeReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmSourceClickRow;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user "last 7 days" rollup that powers the dashboard insight card. We expose totals, the
 * single best-performing link with its top UTM source, and the peak day-of-week/hour bucket — all
 * in the user's stored timezone so the headline ("화 21시") matches what they see elsewhere.
 */
@Service
@RequiredArgsConstructor
public class WeeklyInsightsService {

  private static final Duration WINDOW = Duration.ofDays(7);
  private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

  private final ClickRangeReadRepository clickRanges;
  private final LinkRepository linkRepository;
  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public WeeklyInsights compute(Long userId) {
    Instant now = Instant.now();
    Instant from = now.minus(WINDOW);
    Instant prevFrom = from.minus(WINDOW);

    long totalClicks = clickRanges.countByUserIdAndRange(userId, from, now);
    long humanClicks = clickRanges.countHumanByUserIdAndRange(userId, from, now);
    long previousHumanClicks = clickRanges.countHumanByUserIdAndRange(userId, prevFrom, from);

    Double deltaPercent =
        previousHumanClicks == 0
            ? null
            : (double) (humanClicks - previousHumanClicks) / previousHumanClicks;
    Double humanRatio = totalClicks == 0 ? null : (double) humanClicks / totalClicks;

    WeeklyInsights.TopLink topLink = resolveTopLink(userId, from, now);
    WeeklyInsights.Peak peak = resolvePeak(userId, from, now);

    return new WeeklyInsights(
        from,
        now,
        totalClicks,
        humanClicks,
        previousHumanClicks,
        deltaPercent,
        humanRatio,
        topLink,
        peak);
  }

  private WeeklyInsights.TopLink resolveTopLink(Long userId, Instant from, Instant to) {
    List<LinkClickCount> top =
        clickRanges.findTopLinksByUserIdAndRange(userId, from, to, PageRequest.ofSize(1));
    if (top.isEmpty()) return null;
    LinkClickCount best = top.get(0);
    LinkEntity link = linkRepository.findById(best.getLinkId()).orElse(null);
    if (link == null) return null;
    List<UtmSourceClickRow> utm =
        clickRanges.findTopUtmSourcesByLinkIdAndRange(
            link.getId(), from, to, PageRequest.ofSize(1));
    String topSource = utm.isEmpty() ? null : utm.get(0).getSource();
    return new WeeklyInsights.TopLink(
        link.getShortCode().value(), link.getOriginalUrl(), best.getCount(), topSource);
  }

  private WeeklyInsights.Peak resolvePeak(Long userId, Instant from, Instant to) {
    ZoneId zone = ownerZone(userId);
    String tz = LinkStatsQueryService.currentOffset(zone);
    List<HeatmapRow> rows =
        clickRanges.findHeatmapByUserIdAndRange(userId, from, to, tz, PageRequest.ofSize(1));
    if (rows.isEmpty()) return null;
    HeatmapRow best = rows.get(0);
    return new WeeklyInsights.Peak(best.getDow(), best.getHour(), best.getCount());
  }

  private ZoneId ownerZone(Long userId) {
    return userRepository
        .findById(userId)
        .map(u -> LinkStatsQueryService.safeZone(u.getTimezone()))
        .orElse(DEFAULT_ZONE);
  }
}
