package com.example.short_link.link.destination.application.read;

import com.example.short_link.link.application.write.LinkOwnership;
import com.example.short_link.link.destination.application.dto.DestinationSummary;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LinkDestinationQueryService {

  private final LinkOwnership ownership;
  private final LinkDestinationRepository repository;

  public List<DestinationSummary> list(Long userId, ShortCode shortCode) {
    LinkEntity link = ownedLink(userId, shortCode);
    return repository.findAllByLinkIdOrderByIdAsc(link.linkId().value()).stream()
        .map(LinkDestinationQueryService::toSummary)
        .toList();
  }

  public String blockedCountries(Long userId, ShortCode shortCode) {
    return ownedLink(userId, shortCode).getBlockedCountries();
  }

  private LinkEntity ownedLink(Long userId, ShortCode shortCode) {
    LinkEntity link = ownership.requireOwned(userId, shortCode);
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
