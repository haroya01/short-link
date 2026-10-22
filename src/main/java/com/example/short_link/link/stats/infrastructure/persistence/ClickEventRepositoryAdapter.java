package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import com.example.short_link.link.stats.domain.repository.ClickRollupRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ClickEventRepositoryAdapter implements ClickEventRepository {

  private final JpaClickEventRepository jpa;
  private final JpaClickTotalsReadRepository totals;
  private final ClickRollupRepository rollups;

  @Override
  public ClickEventEntity save(ClickEventEntity event) {
    return jpa.save(event);
  }

  @Override
  public List<ClickEventEntity> saveAll(List<ClickEventEntity> events) {
    return jpa.saveAll(events);
  }

  @Override
  public long count() {
    return jpa.countWithRollups();
  }

  @Override
  public long countByLinkId(Long linkId) {
    return totals.countByLinkId(linkId);
  }

  @Override
  public int deleteByLinkIds(Collection<Long> linkIds) {
    rollups.deleteByLinkIds(linkIds);
    return jpa.deleteByLinkIds(linkIds);
  }

  @Override
  public List<ClickEventEntity> findAllByLinkIdInOrderByClickedAtAsc(Collection<Long> linkIds) {
    return jpa.findAllByLinkIdInOrderByClickedAtAsc(linkIds);
  }

  @Override
  public Map<Long, Map<String, Long>> humanClientAppCountsByLinkIds(Collection<Long> linkIds) {
    Map<Long, Map<String, Long>> counts = new LinkedHashMap<>();
    if (linkIds.isEmpty()) return counts;
    for (Object[] row : jpa.humanClientAppCountsByLinkIds(linkIds)) {
      counts
          .computeIfAbsent(((Number) row[0]).longValue(), id -> new LinkedHashMap<>())
          .put((String) row[1], ((Number) row[2]).longValue());
    }
    return counts;
  }

  @Override
  public List<ClickEventEntity> findEventsByLinkIdBefore(Long linkId, Long cursorId, int limit) {
    return jpa.findEventsByLinkIdBefore(linkId, cursorId, PageRequest.ofSize(limit));
  }

  @Override
  public List<ClickEventEntity> findEventsByLinkIdLatest(Long linkId, int limit) {
    return jpa.findEventsByLinkIdLatest(linkId, PageRequest.ofSize(limit));
  }
}
