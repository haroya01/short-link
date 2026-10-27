package com.example.short_link.link.destination.application.write;

import com.example.short_link.link.application.write.LinkOwnership;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.destination.exception.DestinationErrorCode;
import com.example.short_link.link.destination.exception.DestinationException;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkDestinationOwnership {

  private final LinkOwnership ownership;
  private final LinkDestinationRepository repository;

  LinkEntity ownedLink(Long userId, ShortCode shortCode) {
    LinkEntity link = ownership.requireOwned(userId, shortCode);
    return link;
  }

  LinkDestinationEntity ownedDestination(Long userId, ShortCode shortCode, Long destinationId) {
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
