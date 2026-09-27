package com.example.short_link.admin.infrastructure.persistence;

import com.example.short_link.admin.domain.repository.AdminMetricsRepository;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.LinkMetricRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.LinkStatRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.RecentClickRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.RecentLinkRow;
import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class AdminMetricsRepositoryAdapter implements AdminMetricsRepository {

  private final JpaAdminMetricsRepository jpa;
  private final long smokeUserId;

  AdminMetricsRepositoryAdapter(
      JpaAdminMetricsRepository jpa,
      @Value("${short-link.admin.smoke-user-id:-1}") long smokeUserId) {
    this.jpa = jpa;
    this.smokeUserId = smokeUserId;
  }

  @Override
  public long totalClicks() {
    return jpa.totalClicks(smokeUserId);
  }

  @Override
  public long clicksSince(Instant since) {
    return jpa.clicksSince(since, smokeUserId);
  }

  @Override
  public long linksWithoutClicks() {
    return jpa.linksWithoutClicks();
  }

  @Override
  public List<DailyRow> dailySignupsSince(Instant since, String tz) {
    return jpa.dailySignupsSince(since, tz);
  }

  @Override
  public List<DailyRow> dailyLinksSince(Instant since, String tz) {
    return jpa.dailyLinksSince(since, tz);
  }

  @Override
  public List<DailyRow> dailyClicksSince(Instant since, String tz) {
    return jpa.dailyClicksSince(since, tz, smokeUserId);
  }

  @Override
  public List<LinkMetricRow> linkMetricRowsByShortCodes(Collection<ShortCode> shortCodes) {
    return jpa.linkMetricRowsByShortCodes(shortCodes);
  }

  @Override
  public List<RecentLinkRow> recentLinks(int limit) {
    return jpa.recentLinks(PageRequest.ofSize(limit));
  }

  @Override
  public List<RecentClickRow> recentClicks(int limit) {
    return jpa.recentClicks(smokeUserId, PageRequest.ofSize(limit));
  }

  @Override
  public List<LinkStatRow> topLinksByClicksSince(Instant since, int limit) {
    return jpa.topLinksByClicksSince(since, PageRequest.ofSize(limit));
  }
}
