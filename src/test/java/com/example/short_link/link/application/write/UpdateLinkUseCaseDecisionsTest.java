package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.dto.MyLink;
import com.example.short_link.link.application.read.MyLinkReader;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.og.application.dto.LinkOgFetchRequested;
import com.example.short_link.support.TestEntities;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class UpdateLinkUseCaseDecisionsTest {

  private static final ShortCode CODE = new ShortCode("abc1234");
  private static final Instant LATER = Instant.parse("2026-12-01T00:00:00Z");

  private final LinkOwnership ownership = mock(LinkOwnership.class);
  private final AuditLogService audit = mock(AuditLogService.class);
  private final CreateLinkValidator validator = mock(CreateLinkValidator.class);
  private final MyLinkReader reader = mock(MyLinkReader.class);
  private final LinkCacheEviction cache = mock(LinkCacheEviction.class);
  private final TransactionTemplate transaction = mock(TransactionTemplate.class);
  private final List<Object> events = new ArrayList<>();
  private final UpdateLinkUseCase useCase =
      new UpdateLinkUseCase(
          ownership,
          mock(LinkSidecars.class),
          events::add,
          audit,
          validator,
          reader,
          cache,
          transaction);
  private final LinkEntity link =
      TestEntities.withId(new LinkEntity("https://before", "abc1234", 7L, LATER), 3L);
  private final MyLink view = mock(MyLink.class);

  @BeforeEach
  void runsInsideTheTemplate() {
    when(transaction.execute(any()))
        .thenAnswer(call -> call.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    when(ownership.requireOwned(7L, CODE)).thenReturn(link);
    when(reader.read(link)).thenReturn(view);
  }

  @Test
  void aNewDestinationThatFailsTheCheckChangesNothing() {
    doThrow(new IllegalArgumentException("unsafe")).when(validator).validateUrl("https://bad");

    assertThatThrownBy(
            () -> useCase.execute(new UpdateLinkCommand(7L, CODE, "https://bad", null, null, null)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(link.getOriginalUrl()).isEqualTo("https://before");
  }

  @Test
  void aNewDestinationIsSavedFetchesItsPreviewAndDropsTheCachedRedirect() {
    MyLink result =
        useCase.execute(new UpdateLinkCommand(7L, CODE, "https://after", null, null, null));

    assertThat(result).isSameAs(view);
    assertThat(link.getOriginalUrl()).isEqualTo("https://after");
    assertThat(events).containsExactly(new LinkOgFetchRequested(CODE, "https://after"));
    verify(audit)
        .record(
            AuditAction.LINK_UPDATED,
            "link",
            "abc1234",
            7L,
            Map.of(
                "urlChanged",
                true,
                "fromHost",
                "before",
                "toHost",
                "after",
                "expiresAtChanged",
                false));
    verify(cache).evictAfterCommit(link);
  }

  @Test
  void theSameDestinationAgainIsNotAChange() {
    useCase.execute(new UpdateLinkCommand(7L, CODE, "https://before", null, null, null));

    assertThat(events).isEmpty();
    verify(audit)
        .record(
            AuditAction.LINK_UPDATED,
            "link",
            "abc1234",
            7L,
            Map.of("urlChanged", false, "expiresAtChanged", false));
  }

  @Test
  void expiryChangesOnlyWhenANewOneIsGivenOrItIsCleared() {
    Instant sooner = LATER.minusSeconds(3600);

    useCase.execute(new UpdateLinkCommand(7L, CODE, null, null, "note", null));
    assertThat(link.getExpiresAt()).isEqualTo(LATER);

    useCase.execute(new UpdateLinkCommand(7L, CODE, null, sooner, null, null));
    assertThat(link.getExpiresAt()).isEqualTo(sooner);

    useCase.execute(new UpdateLinkCommand(7L, CODE, null, null, null, null, true));
    assertThat(link.getExpiresAt()).isNull();
    verify(audit, times(2))
        .record(
            AuditAction.LINK_UPDATED,
            "link",
            "abc1234",
            7L,
            Map.of("urlChanged", false, "expiresAtChanged", true));
  }

  @Test
  void aNoteAndAnExpiredMessageAreSavedOnlyWhenSent() {
    useCase.execute(new UpdateLinkCommand(7L, CODE, null, null, "for the flyer", "Sale over"));
    useCase.execute(new UpdateLinkCommand(7L, CODE, null, null, null, null));

    assertThat(link.getNote()).isEqualTo("for the flyer");
    assertThat(link.getExpiredMessage()).isEqualTo("Sale over");
  }
}
