package com.example.short_link.link.access.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.observability.OutcomeResolver;
import com.example.short_link.link.access.application.LinkProtectionService;
import com.example.short_link.link.access.application.TurnstileProperties;
import com.example.short_link.link.access.application.TurnstileVerifier;
import com.example.short_link.link.application.dto.CachedLink;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.redirect.application.LinkRedirectFlow;
import com.example.short_link.link.redirect.application.RedirectOutcome;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class PasswordUnlockControllerTest {

  private final LinkLookupQueryService lookup = mock(LinkLookupQueryService.class);
  private final LinkProtectionService protectionService = mock(LinkProtectionService.class);
  private final LinkRedirectFlow flow = mock(LinkRedirectFlow.class);
  // Turnstile unconfigured (empty secret) → verifier is a no-op, so the gate behaves as before.
  private final TurnstileProperties turnstile = new TurnstileProperties("", "");
  private final TurnstileVerifier turnstileVerifier = new TurnstileVerifier(turnstile);
  private final PasswordUnlockController controller =
      new PasswordUnlockController(lookup, protectionService, flow, turnstile, turnstileVerifier);

  private static final ShortCode CODE = new ShortCode("abc123");

  private MockHttpServletRequest request() {
    return new MockHttpServletRequest("POST", "/abc123");
  }

  private CachedLink cachedLink() {
    return new CachedLink(new LinkId(1L), "https://dst", null, null, null, null);
  }

  @Test
  void wrongPasswordRendersPromptAndSetsPasswordRequiredOutcome() {
    CachedLink link = cachedLink();
    LinkEntity entity = mock(LinkEntity.class);
    when(entity.hasPassword()).thenReturn(true);
    when(lookup.findActiveLink(CODE)).thenReturn(link);
    when(lookup.findEntity(CODE)).thenReturn(Optional.of(entity));
    when(protectionService.checkPassword(entity, "bad")).thenReturn(false);

    MockHttpServletRequest req = request();
    ResponseEntity<?> response = controller.unlock(CODE, "bad", null, null, null, null, null, req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(req.getAttribute(OutcomeResolver.ATTRIBUTE)).isEqualTo("password_required");
    verify(flow, never()).execute(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void correctPasswordTriggersRedirectFlowAnd302() {
    CachedLink link = cachedLink();
    LinkEntity entity = mock(LinkEntity.class);
    when(entity.hasPassword()).thenReturn(true);
    when(lookup.findActiveLink(CODE)).thenReturn(link);
    when(lookup.findEntity(CODE)).thenReturn(Optional.of(entity));
    when(protectionService.checkPassword(entity, "good")).thenReturn(true);
    when(flow.execute(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new RedirectOutcome.Redirect(new CachedLink.Picked("https://dst", null)));

    MockHttpServletRequest req = request();
    ResponseEntity<?> response = controller.unlock(CODE, "good", null, null, null, null, null, req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation().toString()).isEqualTo("https://dst");
    assertThat(req.getAttribute(OutcomeResolver.ATTRIBUTE)).isEqualTo("redirect");
  }

  @Test
  void entryWithoutPasswordSkipsCheckAndProceedsToFlow() {
    CachedLink link = cachedLink();
    LinkEntity entity = mock(LinkEntity.class);
    when(entity.hasPassword()).thenReturn(false);
    when(lookup.findActiveLink(CODE)).thenReturn(link);
    when(lookup.findEntity(CODE)).thenReturn(Optional.of(entity));
    when(flow.execute(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new RedirectOutcome.Redirect(new CachedLink.Picked("https://dst", 42L)));

    MockHttpServletRequest req = request();
    ResponseEntity<?> response =
        controller.unlock(CODE, "ignored", "yt", null, "ref", "ua", "ko-KR", req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    verify(protectionService, never()).checkPassword(any(), any());
    assertThat(req.getAttribute(OutcomeResolver.ATTRIBUTE)).isEqualTo("redirect");
  }

  @Test
  void blockedOutcomeSetsBlockedAttribute() {
    CachedLink link = cachedLink();
    LinkEntity entity = mock(LinkEntity.class);
    when(entity.hasPassword()).thenReturn(false);
    when(lookup.findActiveLink(CODE)).thenReturn(link);
    when(lookup.findEntity(CODE)).thenReturn(Optional.of(entity));
    when(flow.execute(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new RedirectOutcome.Blocked());

    MockHttpServletRequest req = request();
    ResponseEntity<?> response = controller.unlock(CODE, "x", null, null, null, null, null, req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(req.getAttribute(OutcomeResolver.ATTRIBUTE)).isEqualTo("blocked");
  }

  @Test
  void expiredOutcomeSetsExpiredAttribute() {
    CachedLink link = cachedLink();
    LinkEntity entity = mock(LinkEntity.class);
    when(entity.hasPassword()).thenReturn(false);
    when(lookup.findActiveLink(CODE)).thenReturn(link);
    when(lookup.findEntity(CODE)).thenReturn(Optional.of(entity));
    when(flow.execute(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new RedirectOutcome.ExpiredWithMessage("Campaign closed"));

    MockHttpServletRequest req = request();
    ResponseEntity<?> response = controller.unlock(CODE, "x", null, null, null, null, null, req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
    assertThat(req.getAttribute(OutcomeResolver.ATTRIBUTE)).isEqualTo("expired");
  }

  @Test
  void linkNotFoundFromEntityThrowsLinkExceptionAndTagsOutcome() {
    CachedLink link = cachedLink();
    when(lookup.findActiveLink(CODE)).thenReturn(link);
    when(lookup.findEntity(CODE)).thenReturn(Optional.empty());

    MockHttpServletRequest req = request();
    assertThatThrownBy(() -> controller.unlock(CODE, "x", null, null, null, null, null, req))
        .isInstanceOf(LinkException.class)
        .extracting(e -> ((LinkException) e).errorCode())
        .isEqualTo(LinkErrorCode.LINK_NOT_FOUND);
    assertThat(req.getAttribute(OutcomeResolver.ATTRIBUTE)).isEqualTo("not_found");
  }

  @Test
  void viewLimitLinkExceptionTagsViewLimit() {
    CachedLink link = cachedLink();
    LinkEntity entity = mock(LinkEntity.class);
    when(entity.hasPassword()).thenReturn(false);
    when(lookup.findActiveLink(CODE)).thenReturn(link);
    when(lookup.findEntity(CODE)).thenReturn(Optional.of(entity));
    when(flow.execute(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new LinkException(LinkErrorCode.LINK_VIEW_LIMIT_EXCEEDED, CODE));

    MockHttpServletRequest req = request();
    assertThatThrownBy(() -> controller.unlock(CODE, "x", null, null, null, null, null, req))
        .isInstanceOf(LinkException.class);
    assertThat(req.getAttribute(OutcomeResolver.ATTRIBUTE)).isEqualTo("view_limit");
  }

  @Test
  void linkExpiredExceptionTagsExpired() {
    CachedLink link = cachedLink();
    LinkEntity entity = mock(LinkEntity.class);
    when(entity.hasPassword()).thenReturn(false);
    when(lookup.findActiveLink(CODE)).thenReturn(link);
    when(lookup.findEntity(CODE)).thenReturn(Optional.of(entity));
    when(flow.execute(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new LinkException(LinkErrorCode.LINK_EXPIRED, CODE));

    MockHttpServletRequest req = request();
    assertThatThrownBy(() -> controller.unlock(CODE, "x", null, null, null, null, null, req))
        .isInstanceOf(LinkException.class);
    assertThat(req.getAttribute(OutcomeResolver.ATTRIBUTE)).isEqualTo("expired");
  }

  @Test
  void otherLinkExceptionTagsError() {
    when(lookup.findActiveLink(CODE))
        .thenThrow(new LinkException(LinkErrorCode.LINK_NOT_OWNED, CODE));

    MockHttpServletRequest req = request();
    assertThatThrownBy(() -> controller.unlock(CODE, "x", null, null, null, null, null, req))
        .isInstanceOf(LinkException.class);
    assertThat(req.getAttribute(OutcomeResolver.ATTRIBUTE)).isEqualTo("error");
  }
}
