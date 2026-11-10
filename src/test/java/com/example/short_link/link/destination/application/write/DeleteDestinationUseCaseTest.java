package com.example.short_link.link.destination.application.write;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class DeleteDestinationUseCaseTest {

  private final LinkDestinationOwnership ownership = mock(LinkDestinationOwnership.class);
  private final LinkCacheEviction linkCacheEviction = mock(LinkCacheEviction.class);
  private final LinkDestinationRepository repository = mock(LinkDestinationRepository.class);
  private final AuditLogService audit = mock(AuditLogService.class);
  private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
  private final DeleteDestinationUseCase useCase =
      new DeleteDestinationUseCase(ownership, repository, linkCacheEviction, audit, transaction());

  private TransactionTemplate transaction() {
    when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    return new TransactionTemplate(manager);
  }

  @Test
  void executeDeletesOwnedDestination() {
    LinkDestinationEntity dest =
        new LinkDestinationEntity(new LinkId(1L), "https://dest.com", 50, null, null, null, null);
    when(ownership.ownedDestination(42L, new ShortCode("abc1234"), 99L)).thenReturn(dest);

    useCase.execute(42L, new ShortCode("abc1234"), 99L);

    verify(repository).delete(dest);
    Map<String, Object> removed = new HashMap<>();
    removed.put("urlChanged", true);
    removed.put("fromHost", "dest.com");
    removed.put("destinationId", 99L);
    InOrder afterCommit = inOrder(repository, manager, audit);
    afterCommit.verify(repository).delete(dest);
    afterCommit.verify(manager).commit(any());
    afterCommit
        .verify(audit)
        .record(AuditAction.LINK_DESTINATION_DELETED, "link", "abc1234", 42L, removed);
  }
}
