package com.example.short_link.link.health.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.example.short_link.common.net.HttpFetcher;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.common.net.PublicHttpUrlGuard.Resolved;
import com.example.short_link.link.health.application.DestinationCheck.Outcome;
import com.example.short_link.link.health.domain.DestinationFailure;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DestinationProbeTest {

  private static final String URL = "https://shop.example.com/sale";

  private final HttpFetcher fetcher = mock(HttpFetcher.class);
  private final DestinationProbe probe = new DestinationProbe(fetcher);

  private static Optional<Resolved> resolved(String url) {
    return Optional.of(new Resolved(URI.create(url), List.of(InetAddress.getLoopbackAddress())));
  }

  private static HttpFetcher.Response status(int code) {
    return new HttpFetcher.Response(code, Map.of(), new byte[0]);
  }

  private static HttpFetcher.Response redirect(String location) {
    return new HttpFetcher.Response(301, Map.of("Location", List.of(location)), new byte[0]);
  }

  private DestinationCheck checkWith(HttpFetcher.Response... responses) {
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      guard
          .when(() -> PublicHttpUrlGuard.resolve(any()))
          .thenAnswer(inv -> resolved(inv.getArgument(0)));
      var stubbing = when(fetcher.fetch(any(HttpFetcher.Request.class)));
      for (HttpFetcher.Response response : responses) {
        stubbing = stubbing.thenReturn(response);
      }
      return probe.check(URL);
    }
  }

  @Test
  void onlyDefiniteAnswersCountAsBroken() {
    assertThat(checkWith(status(200))).isEqualTo(new DestinationCheck(Outcome.HEALTHY, null, 200));
    assertThat(checkWith(status(404)))
        .isEqualTo(new DestinationCheck(Outcome.BROKEN, DestinationFailure.NOT_FOUND, 404));
    assertThat(checkWith(status(410)))
        .isEqualTo(new DestinationCheck(Outcome.BROKEN, DestinationFailure.GONE, 410));
    for (int wall : new int[] {401, 403, 429, 500, 503}) {
      assertThat(checkWith(status(wall)).outcome())
          .as("status %d", wall)
          .isEqualTo(Outcome.INCONCLUSIVE);
    }
  }

  @Test
  void followsRedirectsAndJudgesWhereTheyLand() {
    assertThat(checkWith(redirect("https://shop.example.com/new"), status(404)).outcome())
        .isEqualTo(Outcome.BROKEN);
    assertThat(checkWith(redirect("/moved"), status(200)).outcome()).isEqualTo(Outcome.HEALTHY);
  }

  @Test
  void endlessRedirectsAndNetworkErrorsAreNotABreakage() {
    HttpFetcher.Response loop = redirect("https://shop.example.com/sale");
    assertThat(checkWith(loop, loop, loop, loop, loop, loop, loop).outcome())
        .isEqualTo(Outcome.INCONCLUSIVE);

    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      guard
          .when(() -> PublicHttpUrlGuard.resolve(any()))
          .thenAnswer(inv -> resolved(inv.getArgument(0)));
      when(fetcher.fetch(any(HttpFetcher.Request.class)))
          .thenThrow(new IllegalStateException("timeout"));
      assertThat(probe.check(URL).outcome()).isEqualTo(Outcome.INCONCLUSIVE);
    }
  }

  @Test
  void aHostThatNoLongerResolvesIsBrokenButAPrivateOneIsSkipped() {
    try (MockedStatic<PublicHttpUrlGuard> guard = mockStatic(PublicHttpUrlGuard.class)) {
      guard.when(() -> PublicHttpUrlGuard.resolve(any())).thenReturn(Optional.empty());
      guard.when(() -> PublicHttpUrlGuard.hostExists(any())).thenReturn(false);
      assertThat(probe.check(URL))
          .isEqualTo(new DestinationCheck(Outcome.BROKEN, DestinationFailure.NO_HOST, null));

      guard.when(() -> PublicHttpUrlGuard.hostExists(any())).thenReturn(true);
      assertThat(probe.check(URL).outcome()).isEqualTo(Outcome.INCONCLUSIVE);
    }
  }
}
