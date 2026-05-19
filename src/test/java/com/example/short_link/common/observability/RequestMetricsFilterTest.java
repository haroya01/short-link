package com.example.short_link.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestMetricsFilterTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-19T10:00:00Z"), ZoneOffset.UTC);
  private final RequestMetricsRecorder recorder = mock(RequestMetricsRecorder.class);
  private final RequestMetricsFilter filter = new RequestMetricsFilter(recorder, clock);

  @Test
  void recordsMetricWithBestMatchingPatternAsRoute() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/r/abc1234");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/r/{shortCode}");
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("shortCode", "abc1234"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(302);

    filter.doFilter(request, response, mock(FilterChain.class));

    ArgumentCaptor<RequestMetric> captor = ArgumentCaptor.forClass(RequestMetric.class);
    verify(recorder, times(1)).record(captor.capture());
    RequestMetric metric = captor.getValue();
    assertThat(metric.route()).isEqualTo("/r/{shortCode}");
    assertThat(metric.shortCode()).isEqualTo("abc1234");
    assertThat(metric.method()).isEqualTo("GET");
    assertThat(metric.status()).isEqualTo(302);
    assertThat(metric.outcome()).isEqualTo("redirect");
  }

  @Test
  void rawUriFallbackWhenPatternAttributeMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health-check");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);

    filter.doFilter(request, response, mock(FilterChain.class));

    ArgumentCaptor<RequestMetric> captor = ArgumentCaptor.forClass(RequestMetric.class);
    verify(recorder).record(captor.capture());
    assertThat(captor.getValue().route()).isEqualTo("/health-check");
  }

  @Test
  void controllerOutcomeAttributeOverridesStatusBasedLabel() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/r/expired");
    request.setAttribute(OutcomeResolver.ATTRIBUTE, "expired");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(410);

    filter.doFilter(request, response, mock(FilterChain.class));

    ArgumentCaptor<RequestMetric> captor = ArgumentCaptor.forClass(RequestMetric.class);
    verify(recorder).record(captor.capture());
    assertThat(captor.getValue().outcome()).isEqualTo("expired");
  }

  @Test
  void actuatorPathsAreSkipped() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, mock(FilterChain.class));

    verify(recorder, never()).record(any());
  }

  @Test
  void metricsReadEndpointIsSkippedToAvoidRecursion() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/v1/admin/metrics/routes");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, mock(FilterChain.class));

    verify(recorder, never()).record(any());
  }
}
