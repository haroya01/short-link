package com.example.short_link.abuse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AbuseReportEntityTest {

  private AbuseReportEntity sample() {
    return new AbuseReportEntity(7L, AbuseSubjectType.POST, 42L, "spam");
  }

  @Test
  void defaultsToOpen() {
    AbuseReportEntity r = sample();
    assertThat(r.getStatus()).isEqualTo(AbuseReportStatus.OPEN);
    assertThat(r.getResolvedAt()).isNull();
    assertThat(r.getAdminNote()).isNull();
  }

  @Test
  void anonymousAllowed() {
    AbuseReportEntity r = new AbuseReportEntity(null, AbuseSubjectType.USER, 1L, null);
    assertThat(r.getReporterUserId()).isNull();
    assertThat(r.getReason()).isNull();
  }

  @Test
  void markReviewingKeepsResolvedAtNull() {
    AbuseReportEntity r = sample();
    r.markReviewing("checking");
    assertThat(r.getStatus()).isEqualTo(AbuseReportStatus.REVIEWING);
    assertThat(r.getAdminNote()).isEqualTo("checking");
    assertThat(r.getResolvedAt()).isNull();
  }

  @Test
  void resolveStampsResolvedAt() {
    AbuseReportEntity r = sample();
    r.resolve("removed");
    assertThat(r.getStatus()).isEqualTo(AbuseReportStatus.RESOLVED);
    assertThat(r.getResolvedAt()).isNotNull();
    assertThat(r.getAdminNote()).isEqualTo("removed");
  }

  @Test
  void rejectStampsResolvedAt() {
    AbuseReportEntity r = sample();
    r.reject("not a violation");
    assertThat(r.getStatus()).isEqualTo(AbuseReportStatus.REJECTED);
    assertThat(r.getResolvedAt()).isNotNull();
  }
}
