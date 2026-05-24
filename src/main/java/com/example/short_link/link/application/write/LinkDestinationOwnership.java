package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.LinkDestinationEntity;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkNotFoundException;
import com.example.short_link.link.exception.LinkNotOwnedException;
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
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkNotOwnedException(shortCode);
    return link;
  }

  LinkDestinationEntity ownedDestination(Long userId, String shortCode, Long destinationId) {
    LinkEntity link = ownedLink(userId, shortCode);
    LinkDestinationEntity dest =
        repository
            .findById(destinationId)
            .orElseThrow(() -> new IllegalArgumentException("destination not found"));
    if (!dest.getLinkId().equals(link.getId())) {
      throw new IllegalArgumentException("destination not found");
    }
    return dest;
  }
}
