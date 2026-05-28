package com.example.short_link.link.application.read;

import com.example.short_link.link.application.dto.MyLink;
import com.example.short_link.link.application.dto.MyLinksCursor;
import com.example.short_link.link.application.dto.MyLinksQuery;
import com.example.short_link.link.application.dto.MyLinksQuery.SortDir;
import com.example.short_link.link.application.dto.MyLinksQuery.SortKey;
import com.example.short_link.link.application.dto.MyLinksResult;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.domain.repository.MyLinksSearchCriteria;
import com.example.short_link.link.stats.domain.repository.ClickTimeReadRepository;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyLinksQueryService {

  private final LinkRepository linkRepository;
  private final ClickTotalsReadRepository clickTotals;
  private final ClickTimeReadRepository clickTime;
  private final LinkTagLookup linkTagService;

  @Transactional(readOnly = true)
  public MyLinksResult myLinks(Long userId, MyLinksQuery query) {
    if (query.sort() == SortKey.CLICK_COUNT) {
      return myLinksSortedByClickCount(userId, query);
    }
    return myLinksSortedByCreatedAt(userId, query);
  }

  private MyLinksResult myLinksSortedByCreatedAt(Long userId, MyLinksQuery query) {
    // Fetch one extra row to detect hasMore without a count() — cursor pagination's whole point is
    // avoiding the OFFSET / COUNT(*) cost on big tables.
    int limit = query.size() + 1;
    MyLinksCursor cursor = query.after();
    List<LinkEntity> raw =
        linkRepository.findMyLinksCreatedAtPage(
            criteria(userId, query),
            cursor == null ? null : cursor.createdAt(),
            cursor == null ? null : cursor.id(),
            query.dir() == SortDir.ASC,
            limit);
    boolean hasMore = raw.size() > query.size();
    List<LinkEntity> links = hasMore ? raw.subList(0, query.size()) : raw;
    if (links.isEmpty()) {
      return new MyLinksResult(List.of(), null, false);
    }
    Map<Long, Long> counts = clickCountsByLinkIds(linkIds(links));
    List<MyLink> items = toItems(links, counts);
    String nextCursor = null;
    if (hasMore) {
      LinkEntity last = links.get(links.size() - 1);
      nextCursor = new MyLinksCursor(last.getCreatedAt(), last.getId()).encode();
    }
    return new MyLinksResult(items, nextCursor, hasMore);
  }

  private MyLinksResult myLinksSortedByClickCount(Long userId, MyLinksQuery query) {
    List<LinkEntity> candidates = linkRepository.findMyLinksCandidates(criteria(userId, query));
    if (candidates.isEmpty()) {
      return new MyLinksResult(List.of(), null, false);
    }

    Map<Long, Long> counts = clickCountsByLinkIds(linkIds(candidates));
    List<LinkEntity> sorted =
        candidates.stream().sorted(clickCountComparator(query.dir(), counts)).toList();
    int start = pageStartAfterClickCursor(sorted, query.after(), counts, query.dir());
    if (start >= sorted.size()) {
      return new MyLinksResult(List.of(), null, false);
    }

    int end = Math.min(start + query.size(), sorted.size());
    boolean hasMore = end < sorted.size();
    List<LinkEntity> links = sorted.subList(start, end);
    List<MyLink> items = toItems(links, counts);

    String nextCursor = null;
    if (hasMore) {
      LinkEntity last = links.get(links.size() - 1);
      nextCursor =
          new MyLinksCursor(
                  last.getCreatedAt(), last.getId(), counts.getOrDefault(last.getId(), 0L))
              .encode();
    }
    return new MyLinksResult(items, nextCursor, hasMore);
  }

  /**
   * Per-link daily click counts for the last 7 days, ordered oldest → newest. Buckets are UTC dates
   * — the sparkline only needs to convey trend shape, so a fixed offset is fine and avoids paying
   * the user-timezone resolution cost on every list-links call.
   */
  private List<MyLink> toItems(List<LinkEntity> links, Map<Long, Long> counts) {
    List<Long> ids = linkIds(links);
    Map<Long, List<String>> tagsByLinkId = linkTagService.tagNamesByLinkIds(ids);
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

  private Map<Long, Long> clickCountsByLinkIds(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    Map<Long, Long> counts = new HashMap<>();
    clickTotals.countsByLinkIds(ids).forEach(row -> counts.put(row.getLinkId(), row.getCount()));
    return counts;
  }

  private Map<Long, List<Long>> sparklineByLinkIds(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    Instant from = Instant.now().minus(Duration.ofDays(7));
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    Map<Long, long[]> byLink = new HashMap<>();
    for (var row : clickTime.findDailyClicksByLinkIdsSince(ids, from)) {
      long offset = ChronoUnit.DAYS.between(row.getDay(), today);
      if (offset < 0 || offset >= 7) continue;
      long[] bucket = byLink.computeIfAbsent(row.getLinkId(), k -> new long[7]);
      bucket[6 - (int) offset] = row.getCount();
    }
    Map<Long, List<Long>> out = new HashMap<>();
    byLink.forEach((linkId, arr) -> out.put(linkId, toList(arr)));
    return out;
  }

  private static List<Long> toList(long[] arr) {
    List<Long> list = new ArrayList<>(arr.length);
    for (long v : arr) list.add(v);
    return list;
  }

  private static List<Long> zeroes() {
    return List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L);
  }

  private MyLinksSearchCriteria criteria(Long userId, MyLinksQuery query) {
    return new MyLinksSearchCriteria(
        userId,
        query.q(),
        linkTagService.linkIdsForTag(userId, query.tag()).orElse(null),
        query.domain(),
        query.expiry(),
        query.createdAfter(),
        query.createdBefore());
  }

  private static List<Long> linkIds(List<LinkEntity> links) {
    return links.stream().map(LinkEntity::getId).toList();
  }

  private static Comparator<LinkEntity> clickCountComparator(SortDir dir, Map<Long, Long> counts) {
    Comparator<LinkEntity> comparator =
        Comparator.comparingLong((LinkEntity link) -> counts.getOrDefault(link.getId(), 0L));
    if (dir == SortDir.DESC) {
      comparator = comparator.reversed();
    }
    return comparator
        .thenComparing(LinkEntity::getCreatedAt, Comparator.reverseOrder())
        .thenComparing(LinkEntity::getId, Comparator.reverseOrder());
  }

  private static int pageStartAfterClickCursor(
      List<LinkEntity> sorted, MyLinksCursor cursor, Map<Long, Long> counts, SortDir dir) {
    if (cursor == null) return 0;
    for (int i = 0; i < sorted.size(); i++) {
      if (sorted.get(i).getId().equals(cursor.id())) {
        return i + 1;
      }
    }
    if (cursor.sortValue() == null) return sorted.size();
    for (int i = 0; i < sorted.size(); i++) {
      if (compareWithClickCursor(sorted.get(i), cursor, counts, dir) > 0) {
        return i;
      }
    }
    return sorted.size();
  }

  private static int compareWithClickCursor(
      LinkEntity link, MyLinksCursor cursor, Map<Long, Long> counts, SortDir dir) {
    long linkCount = counts.getOrDefault(link.getId(), 0L);
    int byCount = Long.compare(linkCount, cursor.sortValue());
    if (dir == SortDir.DESC) {
      byCount = -byCount;
    }
    if (byCount != 0) return byCount;
    int byCreatedAt = cursor.createdAt().compareTo(link.getCreatedAt());
    if (byCreatedAt != 0) return byCreatedAt;
    return Long.compare(cursor.id(), link.getId());
  }
}
