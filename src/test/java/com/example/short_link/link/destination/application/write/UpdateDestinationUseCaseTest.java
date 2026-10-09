package com.example.short_link.link.destination.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.write.CreateLinkValidator;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.exception.DestinationException;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class UpdateDestinationUseCaseTest {

  private final LinkDestinationOwnership ownership = mock(LinkDestinationOwnership.class);
  private final LinkCacheEviction linkCacheEviction = mock(LinkCacheEviction.class);
  private final CreateLinkValidator urlValidator = mock(CreateLinkValidator.class);
  private final UpdateDestinationUseCase useCase =
      new UpdateDestinationUseCase(ownership, linkCacheEviction, urlValidator, transaction());

  private static TransactionTemplate transaction() {
    PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    return new TransactionTemplate(manager);
  }

  private LinkDestinationEntity dest() {
    LinkDestinationEntity d =
        new LinkDestinationEntity(
            new LinkId(1L), "https://old.example.com", 1, null, null, null, null);
    when(ownership.ownedDestination(7L, new ShortCode("abc"), 99L)).thenReturn(d);
    return d;
  }

  @Test
  void executeUpdatesUrl() {
    LinkDestinationEntity d = dest();

    useCase.execute(
        7L,
        new ShortCode("abc"),
        99L,
        "https://new.example.com",
        null,
        null,
        null,
        null,
        null,
        null);

    assertThat(d.getUrl()).isEqualTo("https://new.example.com");
  }

  @Test
  void executeClampsWeightToValidRange() {
    LinkDestinationEntity d = dest();

    useCase.execute(7L, new ShortCode("abc"), 99L, null, 999, null, null, null, null, null);

    assertThat(d.getWeight()).isLessThanOrEqualTo(100);
  }

  @Test
  void executeRejectsNonHttpUrl() {
    dest();

    assertThatThrownBy(
            () ->
                useCase.execute(
                    7L,
                    new ShortCode("abc"),
                    99L,
                    "javascript:alert(1)",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(DestinationException.class);
  }

  @Test
  void executeTogglesEnabled() {
    LinkDestinationEntity d = dest();

    useCase.execute(7L, new ShortCode("abc"), 99L, null, null, null, false, null, null, null);

    assertThat(d.isEnabled()).isFalse();
  }

  @Test
  void executeReturnsSummaryWithUpdatedUrl() {
    dest();

    var out =
        useCase.execute(
            7L,
            new ShortCode("abc"),
            99L,
            "https://new.example.com",
            null,
            null,
            null,
            null,
            null,
            null);

    assertThat(out.url()).isEqualTo("https://new.example.com");
  }

  @Test
  void omittedWeightAndBlankLabelKeepExistingValues() {
    LinkDestinationEntity destination = dest();
    destination.update(null, 37, "existing label", null, null);

    var summary =
        useCase.execute(7L, new ShortCode("abc"), 99L, null, null, "   ", null, null, null, null);

    assertThat(summary.weight()).isEqualTo(37);
    assertThat(summary.label()).isEqualTo("existing label");
    assertThat(summary.url()).isEqualTo("https://old.example.com");
  }

  @Test
  void anUnsafeNewUrlIsRejectedBeforeTheDestinationIsTouched() {
    LinkDestinationEntity d = dest();
    doThrow(new LinkException(LinkErrorCode.MALICIOUS_URL, "hash"))
        .when(urlValidator)
        .validateUrl("https://phish.example.com");

    assertThatThrownBy(
            () ->
                useCase.execute(
                    7L,
                    new ShortCode("abc"),
                    99L,
                    "https://phish.example.com",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(LinkException.class);

    assertThat(d.getUrl()).isEqualTo("https://old.example.com");
    verify(ownership, never()).ownedDestination(any(), any(), any());
  }

  @Test
  void anUpdateWithoutANewUrlSkipsTheSafetyCheck() {
    dest();

    useCase.execute(7L, new ShortCode("abc"), 99L, null, 10, null, null, null, null, null);

    verify(urlValidator, never()).validateUrl(any());
  }
}
