package com.example.short_link.link.application;

import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
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

    List<LinkStats.DailyClick> daily =
        clickRepository.findDailyClicks(linkId, Instant.now().minus(DAILY_WINDOW)).stream()
            .map(r -> new LinkStats.DailyClick(r.getDay().toLocalDate(), r.getCnt()))
            .toList();

    List<LinkStats.ReferrerClick> referrers =
        clickRepository.findReferrerClicks(linkId).stream()
            .map(r -> new LinkStats.ReferrerClick(r.getReferrer(), r.getCnt()))
            .toList();

    Map<String, Long> deviceTotals = new HashMap<>();
    clickRepository
        .findUserAgentClicks(linkId)
        .forEach(
            row -> deviceTotals.merge(classifyDevice(row.getUserAgent()), row.getCnt(), Long::sum));
    List<LinkStats.DeviceClick> devices =
        deviceTotals.entrySet().stream()
            .map(e -> new LinkStats.DeviceClick(e.getKey(), e.getValue()))
            .toList();

    return new LinkStats(shortCode, total, daily, referrers, devices);
  }

  static String classifyDevice(String userAgent) {
    if (userAgent == null) {
      return "unknown";
    }
    String lower = userAgent.toLowerCase();
    if (lower.contains("ipad") || lower.contains("tablet")) {
      return "tablet";
    }
    if (lower.contains("mobile") || lower.contains("iphone") || lower.contains("android")) {
      return "mobile";
    }
    return "desktop";
  }
}
