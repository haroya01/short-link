package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.projection.ClickProjections.CountryClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.DeviceClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.HourClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.ReferrerHostClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.SourceChannelClickRow;
import com.example.short_link.link.stats.domain.repository.ClickAlertReadRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ClickAlertReadRepositoryAdapter implements ClickAlertReadRepository {

  private final JpaClickAlertReadRepository jpa;

  @Override
  public Optional<SourceChannelClickRow> findTopChannelByLinkIdAndRange(
      Long linkId, Instant from, Instant to) {
    return jpa.findTopChannelByLinkIdAndRange(linkId, epoch(from), epoch(to));
  }

  @Override
  public Optional<CountryClickRow> findTopCountryByLinkIdAndRange(
      Long linkId, Instant from, Instant to) {
    return jpa.findTopCountryByLinkIdAndRange(linkId, epoch(from), epoch(to));
  }

  @Override
  public Optional<DeviceClickRow> findTopDeviceByLinkIdAndRange(
      Long linkId, Instant from, Instant to) {
    return jpa.findTopDeviceByLinkIdAndRange(linkId, epoch(from), epoch(to));
  }

  @Override
  public Optional<HourClickRow> findPeakHourByLinkIdAndRange(
      Long linkId, Instant from, Instant to, String tz) {
    return jpa.findPeakHourByLinkIdAndRange(linkId, epoch(from), epoch(to), tz);
  }

  @Override
  public Optional<ReferrerHostClickRow> findTopReferrerHostByLinkIdSince(
      Long linkId, Instant since) {
    return jpa.findTopReferrerHostByLinkIdSince(linkId, epoch(since));
  }

  private static BigDecimal epoch(Instant instant) {
    return BigDecimal.valueOf(instant.getEpochSecond())
        .add(BigDecimal.valueOf(instant.getNano(), 9));
  }
}
