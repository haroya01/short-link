package com.example.short_link.link.application.write;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteLinkUseCase {

  private final LinkOwnership ownership;
  private final LinkRepository repository;
  private final AuditLogService auditLogService;

  @Transactional
  @CacheEvict(value = "link", key = "#command.shortCode()")
  public void execute(DeleteLinkCommand command) {
    LinkEntity link = ownership.requireOwned(command.userId(), command.shortCode());
    repository.delete(link);
    auditLogService.record(
        AuditAction.LINK_DELETED, "link", command.shortCode().value(), command.userId());
  }
}
