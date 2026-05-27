package com.example.short_link.link.stats.infrastructure.persistence;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ClickEventRepositoryAdapter implements ClickEventRepository {

  private final JpaClickEventRepository jpa;

  @Override
  public ClickEventEntity save(ClickEventEntity event) {
    return jpa.save(event);
  }

  @Override
  public long count() {
    return jpa.count();
  }

  @Override
  public long countByLinkId(Long linkId) {
    return jpa.countByLinkId(linkId);
  }

  @Override
  public int deleteByLinkIds(Collection<Long> linkIds) {
    return jpa.deleteByLinkIds(linkIds);
  }

  @Override
  public List<ClickEventEntity> findAllByLinkIdInOrderByClickedAtAsc(Collection<Long> linkIds) {
    return jpa.findAllByLinkIdInOrderByClickedAtAsc(linkIds);
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
