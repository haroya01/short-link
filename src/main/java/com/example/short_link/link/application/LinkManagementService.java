package com.example.short_link.link.application;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.application.dto.LinkOgFetchRequested;
import com.example.short_link.link.application.dto.MyLink;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.link.exception.LinkNotFoundException;
import com.example.short_link.link.exception.LinkNotOwnedException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkManagementService {

  private final LinkRepository repository;
  private final ApplicationEventPublisher events;
  private final AuditLogService auditLogService;
  private final CacheManager cacheManager;

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public MyLink update(
      Long userId,
      String shortCode,
      String originalUrl,
      Instant expiresAt,
      String note,
      String expiredMessage) {
    LinkEntity link = findOwned(userId, shortCode);
    boolean urlChanged = false;
    if (originalUrl != null && !originalUrl.equals(link.getOriginalUrl())) {
      link.changeOriginalUrl(originalUrl);
      urlChanged = true;
    }
    if (expiresAt != null) {
      link.changeExpiresAt(expiresAt);
    }
    if (note != null) link.updateNote(note);
    if (expiredMessage != null) link.updateExpiredMessage(expiredMessage);
    if (urlChanged) {
      events.publishEvent(new LinkOgFetchRequested(link.getShortCode(), link.getOriginalUrl()));
    }
    auditLogService.record(
        AuditAction.LINK_UPDATED,
        "link",
        link.getShortCode(),
        userId,
        Map.of("urlChanged", urlChanged, "expiresAtChanged", expiresAt != null));
    return new MyLink(
        link.getShortCode(),
        link.getOriginalUrl(),
        link.getCreatedAt(),
        link.getExpiresAt(),
        0L,
        java.util.List.of(),
        java.util.List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L));
  }

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public void delete(Long userId, String shortCode) {
    LinkEntity link = findOwned(userId, shortCode);
    repository.delete(link);
    auditLogService.record(AuditAction.LINK_DELETED, "link", shortCode, userId);
  }

  @Transactional
  public int bulkDelete(Long userId, Collection<String> shortCodes) {
    if (shortCodes == null || shortCodes.isEmpty()) return 0;
    List<LinkEntity> owned =
        shortCodes.stream()
            .distinct()
            .map(repository::findByShortCode)
            .flatMap(java.util.Optional::stream)
            .filter(l -> l.isOwnedBy(userId))
            .toList();
    if (owned.isEmpty()) return 0;
    repository.deleteAll(owned);
    Cache cache = cacheManager.getCache("link");
    for (LinkEntity link : owned) {
      if (cache != null) cache.evict(link.getShortCode());
      auditLogService.record(
          AuditAction.LINK_DELETED, "link", link.getShortCode(), userId, Map.of("bulk", true));
    }
    return owned.size();
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
