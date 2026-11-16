package com.example.short_link.link.application.write;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class DeleteLinkUseCase {

  private final LinkOwnership ownership;
  private final LinkRepository repository;
  private final AuditLogService auditLogService;
  private final LinkCacheEviction linkCacheEviction;
  private final TransactionTemplate transaction;

  public void execute(DeleteLinkCommand command) {
    transaction.executeWithoutResult(status -> delete(command));
    auditLogService.record(
        AuditAction.LINK_DELETED, "link", command.shortCode().value(), command.userId());
  }

  private void delete(DeleteLinkCommand command) {
    LinkEntity link = ownership.requireOwned(command.userId(), command.shortCode());
    repository.delete(link);
    linkCacheEviction.evictAfterCommit(link);
  }
}
