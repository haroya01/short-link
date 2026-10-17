package com.example.short_link.federation.application.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.domain.RelayState;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RelayStartupTest {

  private static final String DEV = "https://relay.example/tag/dev";
  private static final String JA = "https://relay.example/tag/puroguramingu";
  private static final String DOWN = "https://down.example/tag/dev";

  private final RelaySubscriptions subscriptions = mock(RelaySubscriptions.class);

  private RelayStartup startup(boolean enabled, List<String> listed) {
    return new RelayStartup(
        subscriptions,
        new RelayProperties(
            enabled, 30, 2_000, 500, Duration.ofSeconds(5), Duration.ofSeconds(4), null, listed));
  }

  private static RelaySubscriptions.View view(String uri, RelayState state) {
    return new RelaySubscriptions.View(1L, uri, null, state, null, Instant.EPOCH, 0);
  }

  @Test
  void eachListedRelayNotSubscribedYetIsSubscribedAndOneThatFailsDoesNotStopTheRest() {
    when(subscriptions.list())
        .thenReturn(new RelaySubscriptions.Listing(List.of(view(DEV, RelayState.ACCEPTED)), 0));
    when(subscriptions.subscribe(DOWN))
        .thenThrow(new FederationException(FederationErrorCode.REMOTE_ACCOUNT_NOT_FOUND, DOWN));
    when(subscriptions.subscribe(JA)).thenReturn(view(JA, RelayState.PENDING));

    assertThat(startup(true, List.of(DEV, DOWN, JA, JA)).subscribeListed()).isEqualTo(1);

    verify(subscriptions, never()).subscribe(DEV);
    verify(subscriptions).subscribe(DOWN);
    verify(subscriptions).subscribe(JA);
  }

  @Test
  void aSecondStartWithEveryListedRelaySubscribedAsksNoOne() {
    when(subscriptions.list())
        .thenReturn(
            new RelaySubscriptions.Listing(
                List.of(view(DEV, RelayState.ACCEPTED), view(JA, RelayState.PENDING)), 0));

    assertThat(startup(true, List.of(DEV, JA)).subscribeListed()).isZero();
    verify(subscriptions, never()).subscribe(DEV);
    verify(subscriptions, never()).subscribe(JA);
  }

  @Test
  void nothingIsSubscribedWhereRelaysAreOff() {
    RelayStartup off = startup(false, List.of(DEV));

    off.ready();
    assertThat(off.subscribeListed()).isZero();

    verifyNoInteractions(subscriptions);
  }
}
