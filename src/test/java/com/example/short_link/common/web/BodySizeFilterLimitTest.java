package com.example.short_link.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class BodySizeFilterLimitTest {

  private static final int DEFAULT_LIMIT = 16 * 1024;

  private final BodySizeFilter filter = new BodySizeFilter(JsonMapper.builder().build());

  private static MockHttpServletRequest declared(String uri, int size) {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
    req.setContent(new byte[size]);
    return req;
  }

  private static MockHttpServletRequest undeclared(byte[] body) {
    MockHttpServletRequest req =
        new MockHttpServletRequest("POST", "/api/v1/links") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    req.setContent(body);
    return req;
  }

  @ParameterizedTest(name = "{0} {1} bytes -> {2}")
  @CsvSource({
    "/api/v1/links, 16384, 200",
    "/api/v1/links, 16385, 413",
    "/api/v1/links/bulk, 1048576, 200",
    "/api/v1/links/bulk, 1048577, 413",
    "/api/v1/posts/7, 1048576, 200",
    "/ap/inbox, 1048577, 413",
  })
  void aDeclaredBodyIsRefusedOnlyPastItsPathsLimit(String uri, int size, int status)
      throws Exception {
    MockHttpServletResponse res = new MockHttpServletResponse();
    AtomicBoolean forwarded = new AtomicBoolean();

    filter.doFilter(declared(uri, size), res, (request, response) -> forwarded.set(true));

    assertThat(res.getStatus()).isEqualTo(status);
    assertThat(forwarded.get()).isEqualTo(status == 200);
  }

  @Test
  void aRefusalSaysWhichLimitWasPassedAsProblemJson() throws Exception {
    MockHttpServletResponse res = new MockHttpServletResponse();

    filter.doFilter(declared("/api/v1/links/bulk", 1024 * 1024 + 1), res, new NoChain());

    assertThat(res.getContentType()).isEqualTo("application/problem+json");
    assertThat(res.getContentAsString()).contains("1024KB limit").contains("PAYLOAD_TOO_LARGE");
  }

  @Test
  void anUndeclaredBodyOfExactlyTheLimitReadsThroughByteByByte() throws Exception {
    byte[] body = new byte[DEFAULT_LIMIT];
    Arrays.fill(body, (byte) 'k');
    byte[] seen = new byte[DEFAULT_LIMIT];
    int[] end = {0};

    filter.doFilter(
        undeclared(body),
        new MockHttpServletResponse(),
        (request, response) -> {
          ServletInputStream in = request.getInputStream();
          for (int i = 0; i < DEFAULT_LIMIT; i++) {
            seen[i] = (byte) in.read();
          }
          end[0] = in.read();
        });

    assertThat(seen).isEqualTo(body);
    assertThat(end[0]).isEqualTo(-1);
  }

  @Test
  void anUndeclaredBodyOfExactlyTheLimitReadsThroughInChunks() throws Exception {
    int[] total = {0};

    filter.doFilter(
        undeclared(new byte[DEFAULT_LIMIT]),
        new MockHttpServletResponse(),
        (request, response) -> total[0] = request.getInputStream().readAllBytes().length);

    assertThat(total[0]).isEqualTo(DEFAULT_LIMIT);
  }

  @Test
  void theByteThatPassesTheLimitIsTheOneRefused() {
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    undeclared(new byte[DEFAULT_LIMIT + 1]),
                    new MockHttpServletResponse(),
                    (request, response) -> {
                      ServletInputStream in = request.getInputStream();
                      for (int i = 0; i < DEFAULT_LIMIT; i++) {
                        in.read();
                      }
                      in.read();
                    }))
        .isInstanceOfSatisfying(
            PayloadTooLargeException.class, e -> assertThat(e.limit()).isEqualTo(DEFAULT_LIMIT));
  }

  private static final class NoChain implements FilterChain {
    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
      throw new AssertionError("an oversized body must not reach the handler");
    }
  }
}
