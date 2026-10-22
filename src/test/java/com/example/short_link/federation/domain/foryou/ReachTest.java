package com.example.short_link.federation.domain.foryou;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReachTest {

  private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");

  @Test
  void aMastodonStatusIsAskedOfItsOwnServerByItsId() {
    assertThat(ReachTarget.of("https://qdon.space/users/yui/statuses/115012345678901234"))
        .contains(new ReachTarget("qdon.space", "115012345678901234"));
    assertThat(ReachTarget.of("https://misskey.example/notes/9abc")).isEmpty();
    assertThat(ReachTarget.of("http://qdon.space/users/yui/statuses/1")).isEmpty();
    assertThat(ReachTarget.of("https://qdon.space/users/yui/statuses/1/activity")).isEmpty();
    assertThat(ReachTarget.of(null)).isEmpty();
  }

  @Test
  void countsAreAskedAnHourSixHoursAndADayAfterTheNoteWasKept() {
    Instant first = ReachSchedule.first(NOW);
    Instant second = ReachSchedule.afterCheck(1, first);
    Instant third = ReachSchedule.afterCheck(2, second);

    assertThat(Duration.between(NOW, first)).isEqualTo(Duration.ofHours(1));
    assertThat(Duration.between(NOW, second)).isEqualTo(Duration.ofHours(6));
    assertThat(Duration.between(NOW, third)).isEqualTo(Duration.ofHours(24));
    assertThat(ReachSchedule.afterCheck(3, third)).isNull();
  }

  @Test
  void aFailedAskWaitsLongerEachTimeAndTheThirdFailureGivesUp() {
    assertThat(ReachSchedule.afterFailure(1, NOW)).isEqualTo(NOW.plus(Duration.ofHours(2)));
    assertThat(ReachSchedule.afterFailure(2, NOW)).isEqualTo(NOW.plus(Duration.ofHours(4)));
    assertThat(ReachSchedule.afterFailure(3, NOW)).isNull();
  }
}
