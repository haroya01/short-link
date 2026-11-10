package com.example.short_link.link.redirect.presentation.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.security.OperatorMarkerStore;
import com.example.short_link.link.redirect.application.RedirectVisit;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class LinkRedirectSupportOperatorMarkerTest {

  private static final String MARKER = "Zm9yLXRoZS1vcGVyYXRvci1tYXJrZXIt";

  private final OperatorMarkerStore markers = mock(OperatorMarkerStore.class);
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/abc1234");

  private RedirectVisit visit() {
    return LinkRedirectSupport.visit(null, "Mozilla/5.0", "ko", null, null, request, markers);
  }

  @Test
  void aRequestWithNoCookieIsAPersonsAndAsksTheStoreNothing() {
    assertThat(visit().visitor()).isEqualTo(RedirectVisit.Visitor.PERSON);

    verifyNoInteractions(markers);
  }

  @Test
  void aRequestWithOnlyOtherCookiesIsAPersonsAndAsksTheStoreNothing() {
    request.setCookies(new Cookie("refresh_token", MARKER), new Cookie("kurl_operator", MARKER));

    assertThat(visit().operator()).isFalse();

    verifyNoInteractions(markers);
  }

  @Test
  void aMarkerTheStoreHoldsMakesTheVisitAnOperators() {
    when(markers.isValid(MARKER)).thenReturn(true);
    request.setCookies(new Cookie("kurl_op", MARKER));

    RedirectVisit visit = visit();

    assertThat(visit.operator()).isTrue();
    assertThat(visit.prefetch()).isFalse();
  }

  @Test
  void aPrefetchFromAnOperatorsBrowserIsAPrefetchAndAsksTheStoreNothing() {
    request.setCookies(new Cookie("kurl_op", MARKER));
    request.addHeader("Sec-Purpose", "prefetch");

    RedirectVisit visit = visit();

    assertThat(visit.prefetch()).isTrue();
    assertThat(visit.operator()).isFalse();
    verifyNoInteractions(markers);
  }

  @Test
  void aMarkerTheStoreDoesNotHoldLeavesTheVisitAPersons() {
    request.setCookies(new Cookie("kurl_op", MARKER));

    assertThat(visit().operator()).isFalse();

    verify(markers).isValid(MARKER);
  }
}
