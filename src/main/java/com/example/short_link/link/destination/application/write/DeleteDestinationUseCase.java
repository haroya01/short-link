package com.example.short_link.link.destination.application.write;

import com.example.short_link.common.audit.AddressChange;
import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.ShortCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteDestinationUseCase {

  private final LinkDestinationOwnership ownership;
  private final LinkDestinationRepository repository;
  private final LinkCacheEviction linkCacheEviction;
  private final AuditLogService auditLogService;

  @Transactional
  public void execute(Long userId, ShortCode shortCode, Long destinationId) {
    LinkDestinationEntity dest = ownership.ownedDestination(userId, shortCode, destinationId);
    repository.delete(dest);
    Map<String, Object> metadata = AddressChange.between(dest.getUrl(), null);
    metadata.put("destinationId", dest.getId());
    auditLogService.record(
        AuditAction.LINK_DESTINATION_DELETED, "link", shortCode.value(), userId, metadata);
    linkCacheEviction.evictAfterCommit(shortCode);
  }
}
