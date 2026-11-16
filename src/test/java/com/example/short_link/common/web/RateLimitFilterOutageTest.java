package com.example.short_link.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterOutageTest {

  private final RateLimitCounter counter = mock(RateLimitCounter.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final RateLimitFilter filter =
      new RateLimitFilter(counter, JsonMapper.builder().build(), 100, 1000, meters);
  private final MockFilterChain chain = new MockFilterChain();

  @BeforeEach
  void redisIsDown() {
    QueryTimeoutException down = new QueryTimeoutException("Redis command timed out");
    when(counter.incrementEndpoint(anyString(), anyString(), any())).thenThrow(down);
    when(counter.incrementAnonymous(any())).thenThrow(down);
    when(counter.incrementUser(anyLong())).thenThrow(down);
  }

  @AfterEach
  void signOut() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void aTwoFactorCodeIsRefusedWithRetryAfterInsteadOfGoingUncounted() throws Exception {
    MockHttpServletResponse res =
        run(new MockHttpServletRequest("POST", "/api/v1/auth/2fa/verify"));

    assertThat(res.getStatus()).isEqualTo(503);
    assertThat(res.getHeader("Retry-After")).isEqualTo("30");
    assertThat(res.getContentAsString()).contains("\"code\":\"TEMPORARILY_UNAVAILABLE\"");
    assertThat(passed()).isFalse();
    assertThat(
            meters
                .counter("rate_limit.unavailable", "scope", "endpoint", "result", "refused")
                .count())
        .isEqualTo(1);
  }

  @Test
  void aTokenRefreshIsRefusedWithRetryAfter() throws Exception {
    MockHttpServletResponse res = run(new MockHttpServletRequest("POST", "/api/v1/auth/refresh"));

    assertThat(res.getStatus()).isEqualTo(503);
    assertThat(passed()).isFalse();
  }

  @Test
  void anAbuseReportStillGoesThroughUncounted() throws Exception {
    run(new MockHttpServletRequest("POST", "/api/v1/public/abuse-reports/links"));

    assertThat(passed()).isTrue();
    assertThat(
            meters
                .counter("rate_limit.unavailable", "scope", "endpoint", "result", "passed")
                .count())
        .isEqualTo(1);
  }

  @Test
  void aVisitorFollowingAShortLinkStillGoesThroughUncounted() throws Exception {
    run(new MockHttpServletRequest("GET", "/abc1234"));

    assertThat(passed()).isTrue();
    assertThat(
            meters
                .counter("rate_limit.unavailable", "scope", "anonymous", "result", "passed")
                .count())
        .isEqualTo(1);
  }

  @Test
  void aSignedInRequestStillGoesThroughUncounted() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(7L, null, List.of()));

    run(new MockHttpServletRequest("GET", "/api/v1/links"));

    assertThat(passed()).isTrue();
    assertThat(
            meters.counter("rate_limit.unavailable", "scope", "user", "result", "passed").count())
        .isEqualTo(1);
  }

  private MockHttpServletResponse run(MockHttpServletRequest req) throws Exception {
    req.setRemoteAddr("203.0.113.7");
    MockHttpServletResponse res = new MockHttpServletResponse();
    filter.doFilter(req, res, chain);
    return res;
  }

  private boolean passed() {
    return chain.getRequest() != null;
  }
}
