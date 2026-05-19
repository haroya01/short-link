package com.example.short_link.profile.visit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProfileVisitSummaryTest {

  @Test
  void recordExposesAllBuckets() {
    ProfileVisitSummary s = new ProfileVisitSummary(10L, 70L, 300L, 1200L);
    assertThat(s.today()).isEqualTo(10L);
    assertThat(s.week()).isEqualTo(70L);
    assertThat(s.month()).isEqualTo(300L);
    assertThat(s.allTime()).isEqualTo(1200L);
  }
}
