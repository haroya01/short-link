package com.example.short_link.federation.application.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.application.FederationProperties;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.domain.RelayMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RelayIntakeTest {

  private static final String POST = "https://mastodon.example/users/alice/statuses/1";

  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final RelayTally tally = new RelayTally(meters);
  private final RelaySubscriptions subscriptions = mock(RelaySubscriptions.class);

  private RelayIntake intake(int queue, int perDay, RelayBreaker breaker) {
    RelayProperties props =
        new RelayProperties(true, 30, perDay, queue, Duration.ofSeconds(5), Duration.ofSeconds(4));
    return new RelayIntake(
        subscriptions,
        breaker,
        tally,
        props,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")),
        meters);
  }

  private RelayIntake intake(int queue, int perDay) {
    RelayProperties props =
        new RelayProperties(true, 30, perDay, queue, Duration.ofSeconds(5), Duration.ofSeconds(4));
    return intake(queue, perDay, new RelayBreaker(props, tally));
  }

  private void mode(RelayMode mode) {
    when(subscriptions.setting()).thenReturn(new RelaySubscriptions.Setting(mode, List.of("ko")));
  }

  @Test
  void anAnnounceIsJudgedByItsAddressAloneAndQueued() {
    mode(RelayMode.COUNT);
    RelayIntake intake = intake(10, 100);

    assertThat(intake.offer(POST)).isEqualTo("queued");
    assertThat(intake.offer(null)).isEqualTo("malformed");
    assertThat(intake.offer("http://mastodon.example/statuses/2")).isEqualTo("malformed");
    assertThat(intake.offer("not an address")).isEqualTo("malformed");
    assertThat(intake.offer("https://mastodon.example/" + "x".repeat(RelayIntake.MAX_URI)))
        .isEqualTo("malformed");
    assertThat(intake.offer("https://kurl.me/ap/notes/1")).isEqualTo("own-server");
    assertThat(intake.offer("https://blog.kurl.me/@yuki/notes/1")).isEqualTo("own-server");

    assertThat(intake.size()).isEqualTo(1);
    assertThat(intake.poll()).isEqualTo(POST);
    assertThat(tally.today()).isEqualTo(Map.of("queued", 1L, "malformed", 4L, "own-server", 2L));
    assertThat(meters.get("federation.relay.queue").gauge().value()).isZero();
  }

  @Test
  void aFullQueueLetsTheOldestGo() {
    mode(RelayMode.COUNT);
    RelayIntake intake = intake(2, 100);

    for (int i = 1; i <= 3; i++) {
      assertThat(intake.offer(POST + i)).isEqualTo("queued");
    }

    List<String> left = new ArrayList<>();
    for (String next = intake.poll(); next != null; next = intake.poll()) {
      left.add(next);
    }
    assertThat(left).containsExactly(POST + 2, POST + 3);
    assertThat(tally.today("queue-full")).isEqualTo(1);
  }

  @Test
  void noMoreAreTakenOnceADaysWorthHasBeen() {
    mode(RelayMode.COUNT);
    RelayIntake intake = intake(10, 2);

    assertThat(intake.offer(POST + 1)).isEqualTo("queued");
    assertThat(intake.offer(POST + 2)).isEqualTo("queued");
    assertThat(intake.offer(POST + 3)).isEqualTo("daily-cap");
    assertThat(intake.size()).isEqualTo(2);
  }

  @Test
  void nothingIsTakenWhileTheSettingIsOffOrTheWorkHasStoppedItself() {
    mode(RelayMode.OFF);
    RelayIntake off = intake(10, 100);
    assertThat(off.offer(POST)).isEqualTo("off");

    mode(RelayMode.COUNT);
    RelayBreaker breaker = mock(RelayBreaker.class);
    when(breaker.tripped()).thenReturn(true);
    RelayIntake stopped = intake(10, 100, breaker);
    assertThat(stopped.offer(POST)).isEqualTo("tripped");

    assertThat(off.size() + stopped.size()).isZero();
  }
}
