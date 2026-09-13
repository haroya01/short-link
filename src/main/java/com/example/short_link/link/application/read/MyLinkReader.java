package com.example.short_link.link.application.read;

import com.example.short_link.link.application.dto.MyLink;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.stats.domain.repository.ClickTimeReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MyLinkReader {

  private final ClickTotalsReadRepository clickTotals;
  private final ClickTimeReadRepository clickTime;
  private final LinkTagLookup linkTags;
  private final Clock clock;

  public MyLinkReader(
      ClickTotalsReadRepository clickTotals,
      ClickTimeReadRepository clickTime,
      LinkTagLookup linkTags,
      Clock clock) {
    this.clickTotals = clickTotals;
    this.clickTime = clickTime;
    this.linkTags = linkTags;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public MyLink read(LinkEntity link) {
    Map<Long, Long> counts = clickCountsByLinkIds(List.of(link.getId()));
    return assemble(List.of(link), counts).getFirst();
  }

  Map<Long, Long> clickCountsByLinkIds(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    Map<Long, Long> counts = new HashMap<>();
    clickTotals.countsByLinkIds(ids).forEach(row -> counts.put(row.getLinkId(), row.getCount()));
    return counts;
  }

  /** 클릭 수 정렬에 사용한 집계를 다시 읽지 않고 같은 값으로 응답을 만든다. */
  List<MyLink> assemble(List<LinkEntity> links, Map<Long, Long> counts) {
    List<Long> ids = links.stream().map(LinkEntity::getId).toList();
    Map<Long, List<String>> tagsByLinkId = linkTags.tagNamesByLinkIds(ids);
    Map<Long, List<Long>> sparkByLinkId = sparklineByLinkIds(ids);
    return links.stream()
        .map(
            link ->
                new MyLink(
                    link.getShortCode(),
                    link.getOriginalUrl(),
                    link.getCreatedAt(),
                    link.getExpiresAt(),
                    counts.getOrDefault(link.getId(), 0L),
                    tagsByLinkId.getOrDefault(link.getId(), List.of()),
                    sparkByLinkId.getOrDefault(link.getId(), zeroes())))
        .toList();
  }

  /** 최근 7개 UTC 날짜를 오래된 날부터 정렬하고, 클릭이 없는 날짜를 0으로 채운다. */
  private Map<Long, List<Long>> sparklineByLinkIds(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    Instant now = clock.instant();
    LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
    Instant from = today.minusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant();
    Map<Long, long[]> byLink = new HashMap<>();
    for (var row : clickTime.findDailyClicksByLinkIdsSince(ids, from)) {
      long offset = ChronoUnit.DAYS.between(row.getDay(), today);
      if (offset < 0 || offset >= 7) continue;
      long[] bucket = byLink.computeIfAbsent(row.getLinkId(), key -> new long[7]);
      bucket[6 - (int) offset] = row.getCount();
    }
    Map<Long, List<Long>> result = new HashMap<>();
    byLink.forEach((linkId, counts) -> result.put(linkId, toList(counts)));
    return result;
  }

  private static List<Long> toList(long[] counts) {
    List<Long> result = new ArrayList<>(counts.length);
    for (long count : counts) result.add(count);
    return result;
  }

  private static List<Long> zeroes() {
    return List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L);
  }
}
