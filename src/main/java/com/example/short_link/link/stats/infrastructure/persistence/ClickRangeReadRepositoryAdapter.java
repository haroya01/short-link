package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.repository.ClickRangeReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.HeatmapRow;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.UtmSourceClickRow;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ClickRangeReadRepositoryAdapter implements ClickRangeReadRepository {

  private final JpaClickRangeReadRepository jpa;

  @Override
  public long countHumanByUserIdAndRange(Long userId, Instant from, Instant to) {
    return jpa.countHumanByUserIdAndRange(userId, from, to);
  }

  @Override
  public long countByUserIdAndRange(Long userId, Instant from, Instant to) {
    return jpa.countByUserIdAndRange(userId, from, to);
  }

  @Override
  public List<LinkClickCount> findTopLinksByUserIdAndRange(
      Long userId, Instant from, Instant to, int limit) {
    return jpa.findTopLinksByUserIdAndRange(userId, from, to, PageRequest.ofSize(limit));
  }

  @Override
  public List<UtmSourceClickRow> findTopUtmSourcesByLinkIdAndRange(
      Long linkId, Instant from, Instant to, int limit) {
    return jpa.findTopUtmSourcesByLinkIdAndRange(linkId, from, to, PageRequest.ofSize(limit));
  }

  @Override
  public List<HeatmapRow> findHeatmapByUserIdAndRange(
      Long userId, Instant from, Instant to, String tz, int limit) {
    return jpa.findHeatmapByUserIdAndRange(userId, from, to, tz, PageRequest.ofSize(limit));
  }

  @Override
  public long countHumanByLinkIdAndRange(Long linkId, Instant from, Instant to) {
    return jpa.countHumanByLinkIdAndRange(linkId, from, to);
  }

  @Override
  public long countBotByLinkIdAndRange(Long linkId, Instant from, Instant to) {
    return jpa.countBotByLinkIdAndRange(linkId, from, to);
  }

  @Override
  public long countUniqueVisitorsByLinkIdAndRange(Long linkId, Instant from, Instant to) {
    return jpa.countUniqueVisitorsByLinkIdAndRange(linkId, from, to);
  }
}
