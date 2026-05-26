package com.example.short_link.link.destination.application.write;

import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.destination.exception.DestinationErrorCode;
import com.example.short_link.link.destination.exception.DestinationException;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkDestinationOwnership {

  private final LinkRepository linkRepository;
  private final LinkDestinationRepository repository;

  LinkEntity ownedLink(Long userId, String shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, shortCode);
    return link;
  }

  LinkDestinationEntity ownedDestination(Long userId, String shortCode, Long destinationId) {
    LinkEntity link = ownedLink(userId, shortCode);
    LinkDestinationEntity dest =
        repository
            .findById(destinationId)
            .orElseThrow(
                () -> new DestinationException(DestinationErrorCode.DESTINATION_NOT_FOUND));
    if (!dest.getLinkId().equals(link.getId())) {
      throw new DestinationException(DestinationErrorCode.DESTINATION_NOT_FOUND);
    }
    return dest;
  }
}
