package com.example.short_link.link.access.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
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
  public boolean setStatsPublic(Long userId, ShortCode shortCode, boolean isPublic) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, shortCode);
    }
    link.changeStatsVisibility(isPublic);
    return link.isStatsPublic();
  }
}
