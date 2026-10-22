package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.application.VisitorSketch;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import com.example.short_link.link.stats.domain.repository.projection.ClickProjections.LinkClickCount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ClickTotalsReadRepositoryAdapter implements ClickTotalsReadRepository {

  private final JpaClickTotalsReadRepository jpa;

  @Override
  public long countHumanByLinkId(Long linkId) {
    return jpa.countHumanByLinkId(linkId);
  }

  @Override
  public long countBotByLinkId(Long linkId) {
    return jpa.countBotByLinkId(linkId);
  }

  @Override
  public long countDirectByLinkId(Long linkId) {
    return jpa.countDirectByLinkId(linkId);
  }

  @Override
  public long countProfileChannelByLinkId(Long linkId) {
    return jpa.countProfileChannelByLinkId(linkId);
  }

  @Override
  public UniqueVisitors uniqueVisitorsByLinkId(Long linkId) {
    JpaClickTotalsReadRepository.RecentVisitorsRow recent = jpa.countRecentVisitors(linkId);
    Instant rawSince = recent.getRolledUntil() <= 1 ? null : epoch(recent.getRolledUntil());
    if (recent.getRolledUp() == 0) return new UniqueVisitors(recent.getRecent(), rawSince);
    return new UniqueVisitors(
        VisitorSketch.count(jpa.findVisitorSketches(linkId), jpa.findRecentVisitorHashes(linkId)),
        rawSince);
  }

  @Override
  public Instant findFirstClickAt(Long linkId) {
    return epoch(jpa.findFirstClickEpoch(linkId));
  }

  @Override
  public Instant findLastClickAt(Long linkId) {
    return epoch(jpa.findLastClickEpoch(linkId));
  }

  @Override
  public long countPreviewByLinkId(Long linkId) {
    return jpa.countPreviewByLinkId(linkId);
  }

  @Override
  public long countSinceByLinkId(Long linkId, Instant since) {
    return jpa.countSinceByLinkId(linkId, since);
  }

  @Override
  public long countDatacenterClicks(Long linkId) {
    return jpa.countDatacenterClicks(linkId);
  }

  @Override
  public List<LinkClickCount> humanCountsByLinkIds(List<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return jpa.humanCountsByLinkIds(ids);
  }

  @Override
  public List<LinkClickCount> countsByLinkIds(List<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return jpa.countsByLinkIds(ids);
  }

  @Override
  public List<LinkClickCount> countsByLinkIdsSince(List<Long> ids, Instant since) {
    if (ids.isEmpty()) return List.of();
    return jpa.countsByLinkIdsSince(ids, decimal(since));
  }

  @Override
  public List<LinkClickCount> countsByLinkIdsBefore(List<Long> ids, Instant before) {
    if (ids.isEmpty()) return List.of();
    return jpa.countsByLinkIdsBefore(ids, decimal(before));
  }

  @Override
  public Instant findLastClickBeforeByLinkIds(List<Long> ids, Instant before) {
    if (ids.isEmpty()) return null;
    return epoch(jpa.findLastClickBeforeEpoch(ids, decimal(before)));
  }

  private static Instant epoch(Long seconds) {
    return seconds == null ? null : Instant.ofEpochSecond(seconds);
  }

  private static BigDecimal decimal(Instant instant) {
    return BigDecimal.valueOf(instant.getEpochSecond())
        .add(BigDecimal.valueOf(instant.getNano(), 9));
  }
}
