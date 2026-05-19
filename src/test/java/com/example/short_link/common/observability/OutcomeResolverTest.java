package com.example.short_link.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class OutcomeResolverTest {

  @Test
  void mapsStatusToCoarseOutcome() {
    assertThat(OutcomeResolver.fromStatus(200)).isEqualTo("ok");
    assertThat(OutcomeResolver.fromStatus(201)).isEqualTo("ok");
    assertThat(OutcomeResolver.fromStatus(302)).isEqualTo("redirect");
    assertThat(OutcomeResolver.fromStatus(308)).isEqualTo("redirect");
    assertThat(OutcomeResolver.fromStatus(304)).isEqualTo("not_modified");
    assertThat(OutcomeResolver.fromStatus(401)).isEqualTo("unauthorized");
    assertThat(OutcomeResolver.fromStatus(403)).isEqualTo("forbidden");
    assertThat(OutcomeResolver.fromStatus(404)).isEqualTo("not_found");
    assertThat(OutcomeResolver.fromStatus(410)).isEqualTo("expired");
    assertThat(OutcomeResolver.fromStatus(429)).isEqualTo("rate_limited");
    assertThat(OutcomeResolver.fromStatus(451)).isEqualTo("blocked");
    assertThat(OutcomeResolver.fromStatus(422)).isEqualTo("client_error");
    assertThat(OutcomeResolver.fromStatus(500)).isEqualTo("error");
    assertThat(OutcomeResolver.fromStatus(503)).isEqualTo("error");
    assertThat(OutcomeResolver.fromStatus(100)).isEqualTo("other");
  }

  @Test
  void requestAttributeOverridesStatusBasedMapping() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(OutcomeResolver.ATTRIBUTE, "custom_outcome");

    assertThat(OutcomeResolver.resolve((HttpServletRequest) request, 500))
        .isEqualTo("custom_outcome");
  }

  @Test
  void blankOverrideFallsBackToStatusBasedMapping() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(OutcomeResolver.ATTRIBUTE, "  ");

    assertThat(OutcomeResolver.resolve((HttpServletRequest) request, 200)).isEqualTo("ok");
  }

  @Test
  void missingAttributeUsesStatusBasedMapping() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    assertThat(OutcomeResolver.resolve((HttpServletRequest) request, 404)).isEqualTo("not_found");
  }
}
