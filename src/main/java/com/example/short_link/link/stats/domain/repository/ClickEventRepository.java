package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ClickEventRepository {

  ClickEventEntity save(ClickEventEntity event);

  List<ClickEventEntity> saveAll(List<ClickEventEntity> events);

  long count();

  long countByLinkId(Long linkId);

  int deleteByLinkIds(Collection<Long> linkIds);

  List<ClickEventEntity> findAllByLinkIdInOrderByClickedAtAsc(Collection<Long> linkIds);

  Map<Long, Map<String, Long>> humanClientAppCountsByLinkIds(Collection<Long> linkIds);

  List<ClickEventEntity> findEventsByLinkIdBefore(Long linkId, Long cursorId, int limit);

  List<ClickEventEntity> findEventsByLinkIdLatest(Long linkId, int limit);
}
