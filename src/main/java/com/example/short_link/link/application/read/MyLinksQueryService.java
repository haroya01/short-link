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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyLinksQueryService {

  private final LinkRepository linkRepository;
  private final MyLinkReader linkReader;
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
    Map<Long, Long> counts = linkReader.clickCountsByLinkIds(linkIds(links));
    List<MyLink> items = linkReader.assemble(links, counts);
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

    Map<Long, Long> counts = linkReader.clickCountsByLinkIds(linkIds(candidates));
    Comparator<ClickPosition> order = clickOrder(query.dir());
    List<LinkEntity> sorted =
        candidates.stream()
            .sorted(Comparator.comparing(link -> clickPosition(link, counts), order))
            .toList();
    int start = pageStartAfterClickCursor(sorted, query.after(), counts, order);
    if (start >= sorted.size()) {
      return new MyLinksResult(List.of(), null, false);
    }

    int end = Math.min(start + query.size(), sorted.size());
    boolean hasMore = end < sorted.size();
    List<LinkEntity> links = sorted.subList(start, end);
    List<MyLink> items = linkReader.assemble(links, counts);

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

  private record ClickPosition(long count, Instant createdAt, Long id) {}

  private static ClickPosition clickPosition(LinkEntity link, Map<Long, Long> counts) {
    return new ClickPosition(
        counts.getOrDefault(link.getId(), 0L), link.getCreatedAt(), link.getId());
  }

  private static Comparator<ClickPosition> clickOrder(SortDir dir) {
    Comparator<ClickPosition> comparator = Comparator.comparingLong(ClickPosition::count);
    if (dir == SortDir.DESC) {
      comparator = comparator.reversed();
    }
    return comparator
        .thenComparing(ClickPosition::createdAt, Comparator.reverseOrder())
        .thenComparing(ClickPosition::id, Comparator.reverseOrder());
  }

  private static int pageStartAfterClickCursor(
      List<LinkEntity> sorted,
      MyLinksCursor cursor,
      Map<Long, Long> counts,
      Comparator<ClickPosition> order) {
    if (cursor == null) return 0;
    for (int i = 0; i < sorted.size(); i++) {
      if (sorted.get(i).getId().equals(cursor.id())) {
        return i + 1;
      }
    }
    if (cursor.sortValue() == null) return sorted.size();
    ClickPosition cursorPosition =
        new ClickPosition(cursor.sortValue(), cursor.createdAt(), cursor.id());
    for (int i = 0; i < sorted.size(); i++) {
      if (order.compare(clickPosition(sorted.get(i), counts), cursorPosition) > 0) {
        return i;
      }
    }
    return sorted.size();
  }
}
