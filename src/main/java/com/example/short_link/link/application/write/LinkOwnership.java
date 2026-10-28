package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkOwnership {

  private final LinkRepository repository;

  public LinkEntity requireOwned(Long userId, ShortCode shortCode) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, shortCode);
    }
    return link;
  }

  public LinkId requireOwnedId(Long userId, ShortCode shortCode) {
    return requireOwned(userId, shortCode).linkId();
  }

  public LinkEntity requireOwnedHidingOthers(Long userId, ShortCode shortCode) {
    return repository
        .findByShortCode(shortCode)
        .filter(link -> link.isOwnedBy(userId))
        .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
  }
}
