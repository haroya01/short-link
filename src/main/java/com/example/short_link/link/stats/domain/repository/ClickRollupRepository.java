package com.example.short_link.link.stats.domain.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClickRollupRepository {

  Instant rolledUntil();

  Optional<Instant> firstClickAtOrAfter(Instant from);

  void rollUpDay(Instant dayStart, Instant dayEnd);

  List<LinkVisitor> humanVisitors(Instant from, Instant until);

  void saveVisitorSketch(Long linkId, Instant dayStart, byte[] sketch);

  void advanceTo(Instant rolledUntil);

  int deleteRawBefore(Instant before, int limit);

  int deleteByLinkIds(Collection<Long> linkIds);

  record LinkVisitor(Long linkId, String visitorHash) {}
}
