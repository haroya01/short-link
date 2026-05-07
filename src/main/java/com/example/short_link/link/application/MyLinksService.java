package com.example.short_link.link.application;

import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyLinksService {

  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

  private final LinkRepository linkRepository;
  private final ClickEventRepository clickRepository;

  @Transactional(readOnly = true)
  public MyLinksResult myLinks(Long userId, MyLinksQuery query) {
    PageRequest pageRequest = PageRequest.of(query.zeroBasedPage(), query.size(), DEFAULT_SORT);
    Page<LinkEntity> page =
        query.q() == null
            ? linkRepository.findAllByUserId(userId, pageRequest)
            : linkRepository.searchByUserId(userId, query.q(), pageRequest);
    List<LinkEntity> links = page.getContent();
    if (links.isEmpty()) {
      return new MyLinksResult(List.of(), page.getTotalElements());
    }
    List<Long> ids = links.stream().map(LinkEntity::getId).toList();
    Map<Long, Long> counts = new HashMap<>();
    clickRepository
        .countsByLinkIds(ids)
        .forEach(row -> counts.put(row.getLinkId(), row.getCount()));
    List<MyLink> items =
        links.stream()
            .map(
                link ->
                    new MyLink(
                        link.getShortCode(),
                        link.getOriginalUrl(),
                        link.getCreatedAt(),
                        link.getExpiresAt(),
                        counts.getOrDefault(link.getId(), 0L)))
            .toList();
    return new MyLinksResult(items, page.getTotalElements());
  }
}
