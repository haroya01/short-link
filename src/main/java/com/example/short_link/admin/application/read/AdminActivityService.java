package com.example.short_link.admin.application.read;

import com.example.short_link.admin.application.dto.AdminActivity;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Live activity feed for the admin console — newest links, newest clicks, and the links trending in
 * the last 24h. Meant to be polled, so every read is a bounded, index-friendly query. Click rows
 * carry only coarse dimensions; IP and visitor hash never leave the persistence layer.
 */
@Service
@RequiredArgsConstructor
public class AdminActivityService {

  private static final int RECENT_LIMIT = 15;
  private static final int TRENDING_LIMIT = 5;
  private static final Duration TRENDING_WINDOW = Duration.ofHours(24);

  private final AdminMetricsRepository metricsRepository;

  @Transactional(readOnly = true)
  public AdminActivity activity() {
    List<AdminActivity.RecentLink> recentLinks =
        metricsRepository.recentLinks(RECENT_LIMIT).stream()
            .map(
                r ->
                    new AdminActivity.RecentLink(
                        r.getShortCode(), r.getOriginalUrl(), r.getOwnerEmail(), r.getCreatedAt()))
            .toList();
    List<AdminActivity.RecentClick> recentClicks =
        metricsRepository.recentClicks(RECENT_LIMIT).stream()
            .map(
                r ->
                    new AdminActivity.RecentClick(
                        r.getShortCode(),
                        r.getClickedAt(),
                        r.getCountryCode(),
                        r.getReferrerHost(),
                        r.getDeviceClass()))
            .toList();
    Instant since = Instant.now().minus(TRENDING_WINDOW);
    List<AdminActivity.TrendingLink> trending =
        metricsRepository.topLinksByClicksSince(since, TRENDING_LIMIT).stream()
            .map(
                r ->
                    new AdminActivity.TrendingLink(
                        r.getShortCode(), r.getOwnerEmail(), r.getCount()))
            .toList();
    return new AdminActivity(recentLinks, recentClicks, trending);
  }
}
