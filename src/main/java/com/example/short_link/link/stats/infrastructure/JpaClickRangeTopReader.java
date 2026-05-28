package com.example.short_link.link.stats.infrastructure;

import com.example.short_link.link.stats.application.ClickRangeTopReader;
import com.example.short_link.link.stats.domain.repository.ClickRangeReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmSourceClickRow;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaClickRangeTopReader implements ClickRangeTopReader {

  private final ClickRangeReadRepository repository;

  @Override
  public List<LinkClickCount> topLinksByUser(Long userId, Instant from, Instant to, int limit) {
    return repository.findTopLinksByUserIdAndRange(userId, from, to, limit);
  }

  @Override
  public List<UtmSourceClickRow> topUtmSourcesByLink(
      Long linkId, Instant from, Instant to, int limit) {
    return repository.findTopUtmSourcesByLinkIdAndRange(linkId, from, to, limit);
  }

  @Override
  public List<HeatmapRow> topHeatmapByUser(
      Long userId, Instant from, Instant to, String tz, int limit) {
    return repository.findHeatmapByUserIdAndRange(userId, from, to, tz, limit);
  }
}
