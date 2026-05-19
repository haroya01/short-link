package com.example.short_link.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class RequestLogFilterTest {

  private final RequestLogFilter filter = new RequestLogFilter();

  @Test
  void shouldNotFilterActuatorAndHealthAndWellKnown() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/actuator/health");
    assertThat(filter.shouldNotFilter(req)).isTrue();
    when(req.getRequestURI()).thenReturn("/healthz");
    assertThat(filter.shouldNotFilter(req)).isTrue();
    when(req.getRequestURI()).thenReturn("/favicon.ico");
    assertThat(filter.shouldNotFilter(req)).isTrue();
    when(req.getRequestURI()).thenReturn("/.well-known/something");
    assertThat(filter.shouldNotFilter(req)).isTrue();
    when(req.getRequestURI()).thenReturn("/api/v1/links");
    assertThat(filter.shouldNotFilter(req)).isFalse();
    when(req.getRequestURI()).thenReturn(null);
    assertThat(filter.shouldNotFilter(req)).isFalse();
  }

  @Test
  void doFilterInternalDelegatesAndLogsInfo() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    when(res.getStatus()).thenReturn(200);
    when(req.getMethod()).thenReturn("GET");
    when(req.getRequestURI()).thenReturn("/x");
    FilterChain chain = mock(FilterChain.class);
    filter.doFilterInternal(req, res, chain);
    verify(chain, times(1)).doFilter(req, res);
  }

  @Test
  void doFilterInternalLogs5xxWarn() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    when(res.getStatus()).thenReturn(500);
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/x");
    FilterChain chain = mock(FilterChain.class);
    filter.doFilterInternal(req, res, chain);
    verify(chain).doFilter(req, res);
  }

  @Test
  void doFilterInternalRethrowsServletException() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    when(res.getStatus()).thenReturn(500);
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/x");
    FilterChain chain = mock(FilterChain.class);
    doThrow(new ServletException("boom")).when(chain).doFilter(any(), any());
    assertThatThrownBy(() -> filter.doFilterInternal(req, res, chain))
        .isInstanceOf(ServletException.class);
  }

  @Test
  void doFilterInternalRethrowsIOException() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    when(res.getStatus()).thenReturn(500);
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/x");
    FilterChain chain = mock(FilterChain.class);
    doThrow(new IOException("boom")).when(chain).doFilter(any(), any());
    assertThatThrownBy(() -> filter.doFilterInternal(req, res, chain))
        .isInstanceOf(IOException.class);
  }

  @Test
  void doFilterInternalRethrowsRuntimeException() throws Exception {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpServletResponse res = mock(HttpServletResponse.class);
    when(res.getStatus()).thenReturn(500);
    when(req.getMethod()).thenReturn("POST");
    when(req.getRequestURI()).thenReturn("/x");
    FilterChain chain = mock(FilterChain.class);
    doThrow(new RuntimeException("boom")).when(chain).doFilter(any(), any());
    assertThatThrownBy(() -> filter.doFilterInternal(req, res, chain))
        .isInstanceOf(RuntimeException.class);
    verify(chain).doFilter(req, res);
    verify(res, never()).setStatus(any(Integer.class).intValue());
  }
}
