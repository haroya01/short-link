package com.example.short_link.link.application.read;

import com.example.short_link.link.application.DestinationSummary;
import com.example.short_link.link.application.LinkNotFoundException;
import com.example.short_link.link.application.LinkNotOwnedException;
import com.example.short_link.link.domain.LinkDestinationEntity;
import com.example.short_link.link.domain.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LinkDestinationQueryService {

  private final LinkRepository linkRepository;
  private final LinkDestinationRepository repository;

  public List<DestinationSummary> list(Long userId, String shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkNotOwnedException(shortCode);
    return repository.findAllByLinkIdOrderByIdAsc(link.getId()).stream()
        .map(LinkDestinationQueryService::toSummary)
        .toList();
  }

  private static DestinationSummary toSummary(LinkDestinationEntity d) {
    return new DestinationSummary(
        d.getId(),
        d.getUrl(),
        d.getWeight(),
        d.getLabel(),
        d.isEnabled(),
        d.getCountryCode(),
        d.getDeviceClass(),
        d.getOs(),
        d.getCreatedAt());
  }
}
