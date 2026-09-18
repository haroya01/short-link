package com.example.short_link.link.application.read;

import com.example.short_link.common.security.UserAccessLookup;
import com.example.short_link.link.application.dto.MyLink;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.stats.domain.repository.ClickTimeReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MyLinkReader {

  private final ClickTotalsReadRepository clickTotals;
  private final ClickTimeReadRepository clickTime;
  private final LinkTagLookup linkTags;
  private final Clock clock;
  private final UserAccessLookup users;

  public MyLinkReader(
      ClickTotalsReadRepository clickTotals,
      ClickTimeReadRepository clickTime,
      LinkTagLookup linkTags,
      Clock clock) {
    this(clickTotals, clickTime, linkTags, clock, null);
  }

  @Autowired
  public MyLinkReader(
      ClickTotalsReadRepository clickTotals,
      ClickTimeReadRepository clickTime,
      LinkTagLookup linkTags,
      Clock clock,
      UserAccessLookup users) {
    this.clickTotals = clickTotals;
    this.clickTime = clickTime;
    this.linkTags = linkTags;
    this.clock = clock;
    this.users = users;
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

  Map<Long, Long> humanClickCountsByLinkIds(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    Map<Long, Long> counts = new HashMap<>();
    clickTotals
        .humanCountsByLinkIds(ids)
        .forEach(row -> counts.put(row.getLinkId(), row.getCount()));
    return counts;
  }

  /** 클릭 수 정렬에 사용한 집계를 다시 읽지 않고 같은 값으로 응답을 만든다. */
  List<MyLink> assemble(List<LinkEntity> links, Map<Long, Long> counts) {
    List<Long> ids = links.stream().map(LinkEntity::getId).toList();
    Map<Long, List<String>> tagsByLinkId = linkTags.tagNamesByLinkIds(ids);
    ZoneId zone = ownerZone(links.isEmpty() ? null : links.getFirst().getUserId());
    Map<Long, List<Long>> sparkByLinkId = dailySeries(ids, zone, clock.instant());
    return assemble(links, counts, tagsByLinkId, sparkByLinkId, zone);
  }

  List<MyLink> assemble(
      List<LinkEntity> links, Map<Long, Long> counts, Map<Long, List<Long>> sparks, ZoneId zone) {
    return assemble(
        links,
        counts,
        linkTags.tagNamesByLinkIds(links.stream().map(LinkEntity::getId).toList()),
        sparks,
        zone);
  }

  private List<MyLink> assemble(
      List<LinkEntity> links,
      Map<Long, Long> counts,
      Map<Long, List<String>> tagsByLinkId,
      Map<Long, List<Long>> sparkByLinkId,
      ZoneId zone) {
    Map<Long, Long> humans =
        humanClickCountsByLinkIds(links.stream().map(LinkEntity::getId).toList());
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
                    sparkByLinkId.getOrDefault(link.getId(), zeroes()),
                    link.getNote(),
                    zone.getId(),
                    humans.getOrDefault(link.getId(), 0L)))
        .toList();
  }

  ZoneId ownerZone(Long userId) {
    if (users == null || userId == null) return ZoneOffset.UTC;
    try {
      return ZoneId.of(users.timezone(userId).orElse("Asia/Seoul"));
    } catch (java.time.DateTimeException invalid) {
      return ZoneId.of("Asia/Seoul");
    }
  }

  Map<Long, List<Long>> dailySeries(List<Long> ids, ZoneId zone, Instant now) {
    if (ids.isEmpty()) return Map.of();
    LocalDate today = now.atZone(zone).toLocalDate();
    List<Instant> starts =
        java.util.stream.IntStream.range(0, 7)
            .mapToObj(i -> today.minusDays(6 - i).atStartOfDay(zone).toInstant())
            .toList();
    Map<Long, long[]> values = new HashMap<>();
    for (var row : clickTime.findDailyClickBucketsByLinkIds(ids, starts, now)) {
      if (row.getBucket() < 0 || row.getBucket() > 6) continue;
      values.computeIfAbsent(row.getLinkId(), key -> new long[7])[row.getBucket()] = row.getCount();
    }
    Map<Long, List<Long>> result = new HashMap<>();
    values.forEach((id, series) -> result.put(id, toList(series)));
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
