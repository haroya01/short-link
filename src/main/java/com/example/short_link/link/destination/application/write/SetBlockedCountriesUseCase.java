package com.example.short_link.link.destination.application.write;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.write.LinkSidecars;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SetBlockedCountriesUseCase {

  private final LinkDestinationOwnership ownership;
  private final LinkSidecars sidecars;
  private final LinkCacheEviction linkCacheEviction;

  @Transactional
  public LinkEntity execute(Long userId, ShortCode shortCode, String csv) {
    LinkEntity link = ownership.ownedLink(userId, shortCode);
    link.setBlockedCountries(csv);
    sidecars.mirrorBlockedCountries(link);
    linkCacheEviction.evictAfterCommit(shortCode);
    return link;
  }
}
