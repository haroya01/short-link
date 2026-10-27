package com.example.short_link.link.application.write;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.dto.MyLink;
import com.example.short_link.link.application.read.MyLinkReader;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.og.application.dto.LinkOgFetchRequested;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class UpdateLinkUseCase {

  private final LinkOwnership ownership;
  private final LinkSidecars sidecars;
  private final ApplicationEventPublisher events;
  private final AuditLogService auditLogService;
  private final CreateLinkValidator validator;
  private final MyLinkReader linkReader;
  private final LinkCacheEviction linkCacheEviction;
  private final TransactionTemplate transaction;

  public MyLink execute(UpdateLinkCommand command) {
    // Safe Browsing HTTP calls stay outside the transaction to avoid holding a JDBC connection.
    if (command.originalUrl() != null) {
      validator.validateUrl(command.originalUrl());
    }
    return transaction.execute(status -> apply(command));
  }

  private MyLink apply(UpdateLinkCommand command) {
    LinkEntity link = ownership.requireOwned(command.userId(), command.shortCode());
    boolean urlChanged = false;
    if (command.originalUrl() != null && !command.originalUrl().equals(link.getOriginalUrl())) {
      link.changeOriginalUrl(command.originalUrl());
      urlChanged = true;
    }
    if (command.expiresAt() != null || command.clearExpiresAt()) {
      link.changeExpiresAt(command.expiresAt());
    }
    if (command.note() != null) link.updateNote(command.note());
    if (command.expiredMessage() != null) {
      link.updateExpiredMessage(command.expiredMessage());
      sidecars.mirrorExpiredMessage(link);
    }
    if (urlChanged) {
      sidecars.resetOgForNewUrl(link);
      events.publishEvent(new LinkOgFetchRequested(link.getShortCode(), link.getOriginalUrl()));
    }
    auditLogService.record(
        AuditAction.LINK_UPDATED,
        "link",
        link.getShortCode().value(),
        command.userId(),
        Map.of(
            "urlChanged",
            urlChanged,
            "expiresAtChanged",
            command.expiresAt() != null || command.clearExpiresAt()));
    linkCacheEviction.evictAfterCommit(link);
    return linkReader.read(link);
  }
}
