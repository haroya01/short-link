package com.example.short_link.link.application;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public MyLink update(Long userId, String shortCode, String originalUrl, Instant expiresAt) {
    LinkEntity link = findOwned(userId, shortCode);
    boolean urlChanged = false;
    if (originalUrl != null && !originalUrl.equals(link.getOriginalUrl())) {
      link.changeOriginalUrl(originalUrl);
      urlChanged = true;
    }
    if (expiresAt != null) {
      link.changeExpiresAt(expiresAt);
    }
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
        link.getShortCode(), link.getOriginalUrl(), link.getCreatedAt(), link.getExpiresAt(), 0L);
  }

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public void delete(Long userId, String shortCode) {
    LinkEntity link = findOwned(userId, shortCode);
    repository.delete(link);
    auditLogService.record(AuditAction.LINK_DELETED, "link", shortCode, userId);
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
