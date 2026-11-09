package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.stats.domain.projection.ClickProjections.LinkClickCount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ClickTotalsReader {

  long countHumanByLinkId(Long linkId);

  long countHumanByLinkIdThrough(Long linkId, Long clickId);

  long countBotByLinkId(Long linkId);

  ClickFlags countFlagsByLinkId(Long linkId);

  UniqueVisitors uniqueVisitorsByLinkId(Long linkId);

  Instant findFirstClickAt(Long linkId);

  Instant findLastClickAt(Long linkId);

  long countSinceByLinkId(Long linkId, Instant since);

  List<LinkClickCount> countsByLinkIds(List<Long> ids);

  List<LinkClickCount> humanCountsByLinkIds(List<Long> ids);

  List<LinkClickCount> countsByLinkIdsSince(List<Long> ids, Instant since);

  List<LinkClickCount> countsByLinkIdsEachSince(Map<Long, Instant> sinceByLinkId);

  List<LinkClickCount> countsByLinkIdsBefore(List<Long> ids, Instant before);

  Instant findLastClickBeforeByLinkIds(List<Long> ids, Instant before);

  Map<Long, Map<String, Long>> humanClientAppCountsByLinkIds(Collection<Long> linkIds);

  record UniqueVisitors(long count, Instant rawSince) {}

  record ClickFlags(long direct, long profile, long preview, long datacenter) {}
}
