package com.example.short_link.link.application;

import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyLinksService {

  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

  private final LinkRepository linkRepository;
  private final ClickEventRepository clickRepository;
  private final LinkTagService linkTagService;

  @Transactional(readOnly = true)
  public MyLinksResult myLinks(Long userId, MyLinksQuery query) {
    PageRequest pageRequest = PageRequest.of(query.zeroBasedPage(), query.size(), DEFAULT_SORT);
    Specification<LinkEntity> spec = buildSpec(userId, query);
    Page<LinkEntity> page = linkRepository.findAll(spec, pageRequest);
    List<LinkEntity> links = page.getContent();
    if (links.isEmpty()) {
      return new MyLinksResult(List.of(), page.getTotalElements());
    }
    List<Long> ids = links.stream().map(LinkEntity::getId).toList();
    Map<Long, Long> counts = new HashMap<>();
    clickRepository
        .countsByLinkIds(ids)
        .forEach(row -> counts.put(row.getLinkId(), row.getCount()));
    Map<Long, List<String>> tagsByLinkId = linkTagService.tagNamesByLinkIds(ids);
    Map<Long, List<Long>> sparkByLinkId = sparklineByLinkIds(ids);
    List<MyLink> items =
        links.stream()
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
    return new MyLinksResult(items, page.getTotalElements());
  }

  /**
   * Per-link daily click counts for the last 7 days, ordered oldest → newest. Buckets are UTC dates
   * — the sparkline only needs to convey trend shape, so a fixed offset is fine and avoids paying
   * the user-timezone resolution cost on every list-links call.
   */
  private Map<Long, List<Long>> sparklineByLinkIds(List<Long> ids) {
    Instant from = Instant.now().minus(Duration.ofDays(7));
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    Map<Long, long[]> byLink = new HashMap<>();
    for (var row : clickRepository.findDailyClicksByLinkIdsSince(ids, from)) {
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

  private Specification<LinkEntity> buildSpec(Long userId, MyLinksQuery query) {
    Instant now = Instant.now();
    Specification<LinkEntity> spec = LinkFilters.ownedBy(userId);
    spec = and(spec, LinkFilters.matchesQuery(query.q()));
    spec = and(spec, LinkFilters.hasTagName(userId, query.tag()));
    spec = and(spec, LinkFilters.domainContains(query.domain()));
    spec = and(spec, LinkFilters.expiry(query.expiry(), now));
    spec = and(spec, LinkFilters.createdAfter(query.createdAfter()));
    spec = and(spec, LinkFilters.createdBefore(query.createdBefore()));
    return spec;
  }

  private static <T> Specification<T> and(Specification<T> base, Specification<T> piece) {
    return piece == null ? base : base.and(piece);
  }
}
