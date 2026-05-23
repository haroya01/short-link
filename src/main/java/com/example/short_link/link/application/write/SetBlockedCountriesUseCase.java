package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.LinkEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SetBlockedCountriesUseCase {

  private final LinkDestinationOwnership ownership;

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public LinkEntity execute(Long userId, String shortCode, String csv) {
    LinkEntity link = ownership.ownedLink(userId, shortCode);
    link.setBlockedCountries(csv);
    return link;
  }
}
