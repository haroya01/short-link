package com.example.short_link.link.stats.application;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmSourceClickRow;
import java.time.Instant;
import java.util.List;

public interface ClickRangeTopReader {
  List<LinkClickCount> topLinksByUser(Long userId, Instant from, Instant to, int limit);

  List<UtmSourceClickRow> topUtmSourcesByLink(Long linkId, Instant from, Instant to, int limit);

  List<HeatmapRow> topHeatmapByUser(Long userId, Instant from, Instant to, String tz, int limit);
}
