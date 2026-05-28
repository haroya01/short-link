package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.CountryClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.DeviceClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HourClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.ReferrerHostClickRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.SourceChannelClickRow;
import java.time.Instant;
import java.util.Optional;

public interface ClickAlertReadRepository {

  Optional<SourceChannelClickRow> findTopChannelByLinkIdAndRange(
      Long linkId, Instant from, Instant to);

  Optional<CountryClickRow> findTopCountryByLinkIdAndRange(Long linkId, Instant from, Instant to);

  Optional<DeviceClickRow> findTopDeviceByLinkIdAndRange(Long linkId, Instant from, Instant to);

  Optional<HourClickRow> findPeakHourByLinkIdAndRange(
      Long linkId, Instant from, Instant to, String tz);

  Optional<ReferrerHostClickRow> findTopReferrerHostByLinkIdSince(Long linkId, Instant since);
}
