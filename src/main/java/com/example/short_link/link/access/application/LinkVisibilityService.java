package com.example.short_link.link.access.application;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.write.LinkOwnership;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkVisibilityService {

  private final LinkOwnership ownership;
  private final LinkCacheEviction linkCacheEviction;

  @Transactional
  public boolean setStatsPublic(Long userId, ShortCode shortCode, boolean isPublic) {
    LinkEntity link = ownership.requireOwned(userId, shortCode);
    link.changeStatsVisibility(isPublic);
    linkCacheEviction.evictAfterCommit(shortCode);
    return link.isStatsPublic();
  }
}
