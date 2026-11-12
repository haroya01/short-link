package com.example.short_link.user.presentation.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.security.OperatorMarkerStore;
import com.example.short_link.user.application.properties.JwtProperties;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OperatorMarkerCookieTest {

  private static final Duration WEB_REFRESH_TTL = Duration.ofDays(14);
  private static final String CARRIED = "Y2FycmllZC1ieS10aGUtYnJvd3Nlci0x";
  private static final String FRESH = "ZnJlc2hseS1taW50ZWQtbWFya2VyLTAx";

  private final OperatorMarkerStore markers = mock(OperatorMarkerStore.class);
  private final OperatorMarkerCookie cookie =
      new OperatorMarkerCookie(markers, true, ".kurl.me", jwt());
  private final MockHttpServletRequest req = new MockHttpServletRequest();
  private final MockHttpServletResponse res = new MockHttpServletResponse();

  private static JwtProperties jwt() {
    return new JwtProperties(
        null,
        null,
        Duration.ofMinutes(15),
        WEB_REFRESH_TTL,
        Duration.ofSeconds(10),
        Duration.ofDays(365));
  }

  private void theBrowserCarries(String marker) {
    req.setCookies(new Cookie("kurl_op", marker));
  }

  private List<String> setCookie() {
    List<String> headers = res.getHeaders(HttpHeaders.SET_COOKIE);
    assertThat(headers).hasSize(1);
    return List.of(headers.get(0).split("; "));
  }

  @Test
  void anAdminLoginSetsAFreshMarkerForTheWholeSiteThatRidesAlongFromOtherSites() {
    when(markers.issue(WEB_REFRESH_TTL)).thenReturn(FRESH);

    cookie.onLogin(req, res, true);

    assertThat(setCookie())
        .contains(
            "kurl_op=" + FRESH,
            "Path=/",
            "Domain=.kurl.me",
            "Max-Age=1209600",
            "Secure",
            "HttpOnly",
            "SameSite=Lax");
  }

  @Test
  void withoutACookieDomainOrTlsTheMarkerIsHostOnlyAndNotSecure() {
    when(markers.issue(WEB_REFRESH_TTL)).thenReturn(FRESH);

    new OperatorMarkerCookie(markers, false, "", jwt()).onLogin(req, res, true);

    assertThat(setCookie())
        .contains("kurl_op=" + FRESH, "Path=/", "HttpOnly", "SameSite=Lax")
        .doesNotContain("Secure")
        .noneMatch(attribute -> attribute.startsWith("Domain="));
  }

  @Test
  void anAdminLoginRevokesTheMarkerTheBrowserCarriedAndMintsANewOne() {
    theBrowserCarries(CARRIED);
    when(markers.issue(WEB_REFRESH_TTL)).thenReturn(FRESH);

    cookie.onLogin(req, res, true);

    verify(markers).revoke(CARRIED);
    verify(markers, never()).extend(any(), any());
    assertThat(setCookie()).contains("kurl_op=" + FRESH, "Max-Age=1209600");
  }

  @Test
  void anAdminRefreshKeepsAndExtendsTheMarkerTheBrowserCarries() {
    theBrowserCarries(CARRIED);
    when(markers.extend(CARRIED, WEB_REFRESH_TTL)).thenReturn(true);

    cookie.onRefresh(req, res, true);

    verify(markers, never()).issue(any());
    verify(markers, never()).revoke(any());
    assertThat(setCookie()).contains("kurl_op=" + CARRIED, "Max-Age=1209600");
  }

  @Test
  void anAdminRefreshMintsAMarkerWhenTheCarriedOneIsNoLongerHeld() {
    theBrowserCarries(CARRIED);
    when(markers.extend(CARRIED, WEB_REFRESH_TTL)).thenReturn(false);
    when(markers.issue(WEB_REFRESH_TTL)).thenReturn(FRESH);

    cookie.onRefresh(req, res, true);

    assertThat(setCookie()).contains("kurl_op=" + FRESH, "Max-Age=1209600");
  }

  @Test
  void anAdminRefreshFromABrowserWithoutAMarkerMintsOne() {
    when(markers.issue(WEB_REFRESH_TTL)).thenReturn(FRESH);

    cookie.onRefresh(req, res, true);

    assertThat(setCookie()).contains("kurl_op=" + FRESH, "Max-Age=1209600");
  }

  @Test
  void aMemberWithoutAMarkerGetsNoCookieAndTheStoreIsNotAsked() {
    cookie.onLogin(req, res, false);
    cookie.onRefresh(req, res, false);

    assertThat(res.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    verifyNoInteractions(markers);
  }

  @Test
  void aMemberLoginFromABrowserCarryingAMarkerRevokesAndClearsIt() {
    theBrowserCarries(CARRIED);

    cookie.onLogin(req, res, false);

    verify(markers).revoke(CARRIED);
    verify(markers, never()).issue(any());
    assertThat(setCookie()).contains("kurl_op=", "Path=/", "Domain=.kurl.me", "Max-Age=0");
  }

  @Test
  void aMemberRefreshFromABrowserCarryingAMarkerRevokesAndClearsIt() {
    theBrowserCarries(CARRIED);

    cookie.onRefresh(req, res, false);

    verify(markers).revoke(CARRIED);
    verify(markers, never()).extend(any(), any());
    verify(markers, never()).issue(any());
    assertThat(setCookie()).contains("kurl_op=", "Path=/", "Domain=.kurl.me", "Max-Age=0");
  }

  @Test
  void logoutRevokesTheCarriedMarkerAndClearsTheCookie() {
    theBrowserCarries(CARRIED);

    cookie.onLogout(req, res);

    verify(markers).revoke(CARRIED);
    assertThat(setCookie()).contains("kurl_op=", "Path=/", "Domain=.kurl.me", "Max-Age=0");
  }

  @Test
  void logoutFromABrowserWithoutAMarkerWritesNoCookie() {
    cookie.onLogout(req, res);

    assertThat(res.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    verifyNoInteractions(markers);
  }

  @Test
  void aRedisFailureWhileMintingLeavesTheSessionWithoutAMarkerInsteadOfFailingIt() {
    when(markers.issue(WEB_REFRESH_TTL)).thenThrow(new RedisConnectionFailureException("away"));

    cookie.onLogin(req, res, true);
    cookie.onRefresh(req, res, true);

    assertThat(res.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
  }

  @Test
  void aRedisFailureWhileRevokingStillClearsTheCookie() {
    theBrowserCarries(CARRIED);
    doThrow(new RedisConnectionFailureException("away")).when(markers).revoke(CARRIED);

    cookie.onLogout(req, res);

    assertThat(setCookie()).contains("kurl_op=", "Max-Age=0");
  }
}
