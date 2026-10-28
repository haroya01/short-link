package com.example.short_link.link.application.write;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.read.MyLinkReader;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class UpdateLinkSidecarMirrorTest {

  private static final ShortCode CODE = new ShortCode("abc1234");

  private final LinkOwnership ownership = mock(LinkOwnership.class);
  private final LinkSidecars sidecars = mock(LinkSidecars.class);
  private final TransactionTemplate transaction = mock(TransactionTemplate.class);
  private final UpdateLinkUseCase useCase =
      new UpdateLinkUseCase(
          ownership,
          sidecars,
          mock(ApplicationEventPublisher.class),
          mock(AuditLogService.class),
          mock(CreateLinkValidator.class),
          mock(MyLinkReader.class),
          mock(LinkCacheEviction.class),
          transaction);
  private final LinkEntity link =
      TestEntities.withId(new LinkEntity("https://before", "abc1234", 7L, null), 3L);

  @BeforeEach
  void runsInsideTheTemplate() {
    when(transaction.execute(any()))
        .thenAnswer(call -> call.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    when(ownership.requireOwned(7L, CODE)).thenReturn(link);
  }

  @Test
  void anExpiredMessageEditMirrorsTheMessageAlone() {
    useCase.execute(new UpdateLinkCommand(7L, CODE, null, null, null, "over", false));

    verify(sidecars).mirrorExpiredMessage(link);
    verify(sidecars, never()).mirrorCampaignExpiration(any());
    verify(sidecars, never()).resetOgForNewUrl(any());
  }

  @Test
  void aNewDestinationResetsTheCardWithoutTouchingTheExpiryMirror() {
    useCase.execute(new UpdateLinkCommand(7L, CODE, "https://after", null, null, null, false));

    verify(sidecars).resetOgForNewUrl(link);
    verify(sidecars, never()).mirrorExpiredMessage(any());
  }
}
