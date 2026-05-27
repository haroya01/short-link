package com.example.short_link.admin.infrastructure.persistence;

import com.example.short_link.admin.domain.repository.AdminMetricsRepository;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.LinkMetricRow;
import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class AdminMetricsRepositoryAdapter implements AdminMetricsRepository {

  private final JpaAdminMetricsRepository jpa;

  @Override
  public long totalClicks() {
    return jpa.totalClicks();
  }

  @Override
  public long clicksSince(Instant since) {
    return jpa.clicksSince(since);
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
    return jpa.dailyClicksSince(since, tz);
  }

  @Override
  public List<LinkMetricRow> linkMetricRowsByShortCodes(Collection<ShortCode> shortCodes) {
    return jpa.linkMetricRowsByShortCodes(shortCodes);
  }
}
