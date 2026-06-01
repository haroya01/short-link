package com.example.short_link.link.destination.application.read;

import com.example.short_link.link.destination.application.dto.DestinationSummary;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
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

  public List<DestinationSummary> list(Long userId, ShortCode shortCode) {
    LinkEntity link = ownedLink(userId, shortCode);
    return repository.findAllByLinkIdOrderByIdAsc(link.linkId().value()).stream()
        .map(LinkDestinationQueryService::toSummary)
        .toList();
  }

  /** Current blocked-country codes (CSV, possibly empty) for a link the caller owns. */
  public String blockedCountries(Long userId, ShortCode shortCode) {
    return ownedLink(userId, shortCode).getBlockedCountries();
  }

  private LinkEntity ownedLink(Long userId, ShortCode shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, shortCode);
    return link;
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
