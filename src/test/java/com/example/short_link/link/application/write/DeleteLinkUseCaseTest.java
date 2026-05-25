package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import org.junit.jupiter.api.Test;

class DeleteLinkUseCaseTest {

  private final LinkOwnership ownership = mock(LinkOwnership.class);
  private final LinkRepository repository = mock(LinkRepository.class);
  private final AuditLogService auditLogService = mock(AuditLogService.class);
  private final DeleteLinkUseCase useCase =
      new DeleteLinkUseCase(ownership, repository, auditLogService);

  @Test
  void executeDeletesLinkOwnedByUser() {
    LinkEntity link = new LinkEntity("https://x.com", "abc1234", 42L, null);
    when(ownership.requireOwned(42L, "abc1234")).thenReturn(link);

    useCase.execute(new DeleteLinkCommand(42L, "abc1234"));

    verify(repository).delete(link);
  }

  @Test
  void executeRecordsAuditLog() {
    LinkEntity link = new LinkEntity("https://x.com", "abc1234", 42L, null);
    when(ownership.requireOwned(42L, "abc1234")).thenReturn(link);

    useCase.execute(new DeleteLinkCommand(42L, "abc1234"));

    verify(auditLogService)
        .record(eq(AuditAction.LINK_DELETED), eq("link"), eq("abc1234"), eq(42L));
  }

  @Test
  void executePropagatesOwnershipFailure() {
    when(ownership.requireOwned(any(), any())).thenThrow(LinkException.class);

    assertThatThrownBy(() -> useCase.execute(new DeleteLinkCommand(42L, "nope0001")))
        .isInstanceOf(LinkException.class);
    verify(repository, never()).delete(any(LinkEntity.class));
  }
}
