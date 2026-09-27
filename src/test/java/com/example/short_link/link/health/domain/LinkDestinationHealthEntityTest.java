package com.example.short_link.link.health.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.domain.LinkId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LinkDestinationHealthEntityTest {

  private static final String URL = "https://shop.example.com/sale";
  private static final Instant T0 = Instant.parse("2026-09-27T00:00:00Z");

  private static LinkDestinationHealthEntity checked(String url) {
    LinkDestinationHealthEntity health = new LinkDestinationHealthEntity(new LinkId(1L));
    health.startCheckOf(url);
    return health;
  }

  @Test
  void theOwnerHearsOnceWhenTheSecondFailureConfirmsIt() {
    LinkDestinationHealthEntity health = checked(URL);

    assertThat(health.markFailed(DestinationFailure.NOT_FOUND, 404, T0, 2)).isFalse();
    assertThat(health.isBrokenFor(URL)).isFalse();
    assertThat(health.markFailed(DestinationFailure.NOT_FOUND, 404, T0.plusSeconds(3600), 2))
        .isTrue();
    assertThat(health.isBrokenFor(URL)).isTrue();
    assertThat(health.markFailed(DestinationFailure.NOT_FOUND, 404, T0.plusSeconds(7200), 2))
        .isFalse();
  }

  @Test
  void anInconclusiveCheckNeitherConfirmsNorClears() {
    LinkDestinationHealthEntity health = checked(URL);
    health.markFailed(DestinationFailure.NOT_FOUND, 404, T0, 2);

    health.markInconclusive(T0.plusSeconds(60));

    assertThat(health.getFailures()).isEqualTo(1);
    assertThat(health.getCheckedAt()).isEqualTo(T0.plusSeconds(60));
  }

  @Test
  void recoveringOrMovingTheLinkStartsClean() {
    LinkDestinationHealthEntity health = checked(URL);
    health.markFailed(DestinationFailure.GONE, 410, T0, 1);
    assertThat(health.isBrokenFor(URL)).isTrue();

    health.markHealthy(T0.plusSeconds(60));
    assertThat(health.isBrokenFor(URL)).isFalse();

    health.markFailed(DestinationFailure.GONE, 410, T0.plusSeconds(120), 1);
    assertThat(health.isBrokenFor("https://shop.example.com/moved")).isFalse();
    health.startCheckOf("https://shop.example.com/moved");
    assertThat(health.getFailures()).isZero();
    assertThat(health.getBrokenSince()).isNull();
  }
}
