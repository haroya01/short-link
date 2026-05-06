package com.example.short_link.link.application;

import com.example.short_link.link.domain.ClickEventRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyLinksService {

  private final LinkRepository linkRepository;
  private final ClickEventRepository clickRepository;

  @Transactional(readOnly = true)
  public List<MyLink> myLinks(Long userId) {
    List<LinkEntity> links = linkRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    if (links.isEmpty()) {
      return List.of();
    }
    List<Long> ids = links.stream().map(LinkEntity::getId).toList();
    Map<Long, Long> counts = new HashMap<>();
    clickRepository
        .countsByLinkIds(ids)
        .forEach(row -> counts.put(row.getLinkId(), row.getCount()));
    return links.stream()
        .map(
            link ->
                new MyLink(
                    link.getShortCode(),
                    link.getOriginalUrl(),
                    link.getCreatedAt(),
                    link.getExpiresAt(),
                    counts.getOrDefault(link.getId(), 0L)))
        .toList();
  }
}
