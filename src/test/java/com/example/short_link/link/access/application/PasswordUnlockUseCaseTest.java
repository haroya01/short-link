package com.example.short_link.link.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.link.access.application.write.PasswordUnlockUseCase;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.redirect.application.LinkRedirectFlow;
import com.example.short_link.link.redirect.application.RedirectVisit;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PasswordUnlockUseCaseTest {
  private final LinkLookupQueryService lookup = mock(LinkLookupQueryService.class);
  private final LinkProtectionService passwords = mock(LinkProtectionService.class);
  private final PasswordAttempts attempts = mock(PasswordAttempts.class);
  private final LinkRedirectFlow flow = mock(LinkRedirectFlow.class);
  private final TurnstileVerifier captcha = mock(TurnstileVerifier.class);
  private final PasswordUnlockUseCase useCase =
      new PasswordUnlockUseCase(lookup, passwords, attempts, flow, captcha);
  private final ShortCode code = new ShortCode("abc123");
  private final RedirectVisit visit =
      new RedirectVisit("referrer", "agent", "192.0.2.1", "ko", "yt", null, false, null, false);

  @Test
  void captchaFailureDoesNotReadLinkOrChangePasswordAttempts() {
    when(captcha.enabled()).thenReturn(true);
    when(captcha.verify("invalid", null)).thenReturn(false);

    assertThat(useCase.execute(code, "password", "invalid", visit))
        .isEqualTo(
            new PasswordUnlockResult.Rejected(PasswordUnlockResult.RejectionReason.CAPTCHA_FAILED));
    verifyNoInteractions(lookup, passwords, attempts, flow);
  }

  @Test
  void lockedOutVisitDoesNotReadLinkOrCheckPassword() {
    when(attempts.isLockedOut(code.value(), visit.clientIp())).thenReturn(true);

    assertThat(useCase.execute(code, "password", null, visit))
        .isEqualTo(
            new PasswordUnlockResult.Rejected(PasswordUnlockResult.RejectionReason.LOCKED_OUT));
    verifyNoInteractions(lookup, passwords, flow);
    verify(attempts, never()).recordFailure(any(), any());
    verify(attempts, never()).reset(any(), any());
  }

  @Test
  void wrongPasswordRecordsFailureWithoutResetOrRedirect() {
    LinkEntity entity = loadPasswordLink();
    when(passwords.checkPassword(entity, "wrong")).thenReturn(false);

    assertThat(useCase.execute(code, "wrong", null, visit))
        .isEqualTo(
            new PasswordUnlockResult.Rejected(PasswordUnlockResult.RejectionReason.WRONG_PASSWORD));
    var order = inOrder(attempts, lookup, passwords);
    order.verify(attempts).isLockedOut(code.value(), visit.clientIp());
    order.verify(lookup).findActiveLink(code);
    order.verify(lookup).findEntity(code);
    order.verify(passwords).checkPassword(entity, "wrong");
    order.verify(attempts).recordFailure(code.value(), visit.clientIp());
    verify(attempts, never()).reset(any(), any());
    verifyNoInteractions(flow);
  }

  @Test
  void successfulPasswordResetsAttemptsBeforeRedirectEvenWhenViewLimitRejects() {
    LinkEntity entity = loadPasswordLink();
    when(passwords.checkPassword(entity, "correct")).thenReturn(true);
    when(flow.execute(any(), any(), any()))
        .thenThrow(new LinkException(LinkErrorCode.LINK_VIEW_LIMIT_EXCEEDED, code));

    assertThatThrownBy(() -> useCase.execute(code, "correct", null, visit))
        .isInstanceOf(LinkException.class);
    var order = inOrder(passwords, attempts, flow);
    order.verify(passwords).checkPassword(entity, "correct");
    order.verify(attempts).reset(code.value(), visit.clientIp());
    order.verify(flow).execute(any(), any(), any());
    verify(attempts, never()).recordFailure(any(), any());
  }

  private LinkEntity loadPasswordLink() {
    LinkEntity entity = mock(LinkEntity.class);
    when(entity.hasPassword()).thenReturn(true);
    when(lookup.findActiveLink(code))
        .thenReturn(new CachedLink(new LinkId(7L), "https://destination", null, null, null, null));
    when(lookup.findEntity(code)).thenReturn(Optional.of(entity));
    return entity;
  }
}
