package com.example.short_link.link.application.write;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.application.dto.MyLink;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.expiration.domain.LinkExpirationPolicyEntity;
import com.example.short_link.link.expiration.domain.repository.LinkExpirationPolicyRepository;
import com.example.short_link.link.og.application.dto.LinkOgFetchRequested;
import com.example.short_link.link.og.domain.LinkOgMetadataEntity;
import com.example.short_link.link.og.domain.repository.LinkOgMetadataRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateLinkUseCase {

  private final LinkOwnership ownership;
  private final LinkOgMetadataRepository ogMetadataRepository;
  private final LinkExpirationPolicyRepository expirationPolicyRepository;
  private final ApplicationEventPublisher events;
  private final AuditLogService auditLogService;

  @Transactional
  @CacheEvict(value = "link", key = "#command.shortCode()")
  public MyLink execute(UpdateLinkCommand command) {
    LinkEntity link = ownership.requireOwned(command.userId(), command.shortCode());
    boolean urlChanged = false;
    if (command.originalUrl() != null && !command.originalUrl().equals(link.getOriginalUrl())) {
      link.changeOriginalUrl(command.originalUrl());
      urlChanged = true;
    }
    if (command.expiresAt() != null) {
      link.changeExpiresAt(command.expiresAt());
    }
    if (command.note() != null) link.updateNote(command.note());
    if (command.expiredMessage() != null) {
      link.updateExpiredMessage(command.expiredMessage());
      LinkExpirationPolicyEntity policy =
          expirationPolicyRepository
              .findById(link.getId())
              .orElseGet(() -> new LinkExpirationPolicyEntity(link.getId()));
      policy.changeExpiredMessage(link.getExpiredMessage());
      expirationPolicyRepository.save(policy);
    }
    if (urlChanged) {
      LinkOgMetadataEntity ogMeta =
          ogMetadataRepository
              .findById(link.getId())
              .orElseGet(() -> new LinkOgMetadataEntity(link.getId()));
      ogMeta.resetForNewUrl();
      ogMetadataRepository.save(ogMeta);
      events.publishEvent(new LinkOgFetchRequested(link.getShortCode(), link.getOriginalUrl()));
    }
    auditLogService.record(
        AuditAction.LINK_UPDATED,
        "link",
        link.getShortCode().value(),
        command.userId(),
        Map.of("urlChanged", urlChanged, "expiresAtChanged", command.expiresAt() != null));
    return new MyLink(
        link.getShortCode(),
        link.getOriginalUrl(),
        link.getCreatedAt(),
        link.getExpiresAt(),
        0L,
        List.of(),
        List.of(0L, 0L, 0L, 0L, 0L, 0L, 0L));
  }
}
