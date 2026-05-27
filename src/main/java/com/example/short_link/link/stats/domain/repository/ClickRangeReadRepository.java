package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmSourceClickRow;
import java.time.Instant;
import java.util.List;

public interface ClickRangeReadRepository {

  long countHumanByUserIdAndRange(Long userId, Instant from, Instant to);

  long countByUserIdAndRange(Long userId, Instant from, Instant to);

  List<LinkClickCount> findTopLinksByUserIdAndRange(
      Long userId, Instant from, Instant to, int limit);

  List<UtmSourceClickRow> findTopUtmSourcesByLinkIdAndRange(
      Long linkId, Instant from, Instant to, int limit);

  List<HeatmapRow> findHeatmapByUserIdAndRange(
      Long userId, Instant from, Instant to, String tz, int limit);

  long countHumanByLinkIdAndRange(Long linkId, Instant from, Instant to);

  long countBotByLinkIdAndRange(Long linkId, Instant from, Instant to);

  long countUniqueVisitorsByLinkIdAndRange(Long linkId, Instant from, Instant to);
}
