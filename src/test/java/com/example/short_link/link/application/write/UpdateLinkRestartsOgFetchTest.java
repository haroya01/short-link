package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class UpdateLinkRestartsOgFetchTest {

  private static final ShortCode CODE = new ShortCode("abc1234");

  private final LinkOwnership ownership = mock(LinkOwnership.class);
  private final LinkRepository links = mock(LinkRepository.class);
  private final TransactionTemplate transaction = mock(TransactionTemplate.class);
  private final UpdateLinkUseCase useCase =
      new UpdateLinkUseCase(
          ownership,
          links,
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
  void aNewDestinationRestartsTheFetchedPreview() {
    TestEntities.setField(link, "ogTitle", "before");

    useCase.execute(new UpdateLinkCommand(7L, CODE, "https://after", null, null, null, false));

    verify(links).restartOgFetch(3L);
    assertThat(link.getOgTitle()).isNull();
  }

  @Test
  void theSameDestinationKeepsTheFetchedPreview() {
    useCase.execute(new UpdateLinkCommand(7L, CODE, "https://before", null, null, null, false));

    verify(links, never()).restartOgFetch(any());
  }

  @Test
  void anExpiredMessageEditLeavesTheFetchedPreviewAlone() {
    useCase.execute(new UpdateLinkCommand(7L, CODE, null, null, null, "over", false));

    verify(links, never()).restartOgFetch(any());
    assertThat(link.getExpiredMessage()).isEqualTo("over");
  }
}
