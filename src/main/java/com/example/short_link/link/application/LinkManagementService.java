package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkManagementService {

  private final LinkRepository repository;

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public MyLink update(Long userId, String shortCode, String originalUrl, Instant expiresAt) {
    LinkEntity link = findOwned(userId, shortCode);
    if (originalUrl != null) {
      link.changeOriginalUrl(originalUrl);
    }
    if (expiresAt != null) {
      link.changeExpiresAt(expiresAt);
    }
    return new MyLink(
        link.getShortCode(), link.getOriginalUrl(), link.getCreatedAt(), link.getExpiresAt(), 0L);
  }

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public void delete(Long userId, String shortCode) {
    LinkEntity link = findOwned(userId, shortCode);
    repository.delete(link);
  }

  private LinkEntity findOwned(Long userId, String shortCode) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkNotOwnedException(shortCode);
    }
    return link;
  }
}
