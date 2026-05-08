package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkVisibilityService {

  private final LinkRepository repository;

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public boolean setStatsPublic(Long userId, String shortCode, boolean isPublic) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkNotOwnedException(shortCode);
    }
    link.changeStatsVisibility(isPublic);
    return link.isStatsPublic();
  }
}
