package com.example.short_link.link.stats.domain.repository;

import com.example.short_link.link.stats.domain.ClickEventEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ClickEventRepository {

  ClickEventEntity save(ClickEventEntity event);

  List<ClickEventEntity> saveAll(List<ClickEventEntity> events);

  long count();

  int deleteByLinkIds(Collection<Long> linkIds);

  List<ClickEventEntity> findAllByLinkIdInOrderByClickedAtAsc(Collection<Long> linkIds);

  List<ClickEventEntity> findEventsByLinkIdBefore(Long linkId, Long cursorId, int limit);

  List<ClickEventEntity> findEventsByLinkIdLatest(Long linkId, int limit);

  int markHumansAs(Long linkId, Collection<Long> clickIds, String botName, Instant rolledUntil);

  int markAsHumans(Long linkId, Collection<Long> clickIds, String botName, Instant rolledUntil);

  List<Instant> findDayStartsOf(
      Long linkId, Collection<Long> clickIds, Instant from, Instant before);
}
