package com.example.short_link.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Exercises the request-body stream wrapper directly (the {@code Content-Length} fast-path is
 * covered by {@link BodySizeFilterTest}). A request that hides its size — chunked transfer, so
 * {@code getContentLengthLong()} is -1 — must still be capped when the body is actually read.
 */
class BodySizeFilterStreamTest {

  private final BodySizeFilter filter = new BodySizeFilter(JsonMapper.builder().build());

  /**
   * MockHttpServletRequest reports its content length from setContent; force -1 to mimic chunked.
   */
  private static MockHttpServletRequest lengthlessRequest(String uri, byte[] body) {
    MockHttpServletRequest req =
        new MockHttpServletRequest("POST", uri) {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    req.setContent(body);
    return req;
  }

  @Test
  void streamReadBeyondCapThrowsEvenWithoutContentLength() {
    MockHttpServletRequest req = lengthlessRequest("/api/v1/links", new byte[20 * 1024]);
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    req,
                    new MockHttpServletResponse(),
                    (request, response) -> {
                      ServletInputStream in = request.getInputStream();
                      byte[] buf = new byte[4096];
                      while (in.read(buf) != -1) {
                        // drain — the wrapper must abort before the whole body is read
                      }
                    }))
        .isInstanceOf(PayloadTooLargeException.class);
  }

  @Test
  void singleByteReadBeyondCapThrows() {
    MockHttpServletRequest req = lengthlessRequest("/api/v1/links", new byte[20 * 1024]);
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    req,
                    new MockHttpServletResponse(),
                    (request, response) -> {
                      ServletInputStream in = request.getInputStream();
                      while (in.read() != -1) {
                        // drain byte-by-byte
                      }
                    }))
        .isInstanceOf(PayloadTooLargeException.class);
  }

  @Test
  void bodyUnderCapReadsThroughViaReader() throws Exception {
    MockHttpServletRequest req = lengthlessRequest("/api/v1/links", "hello".getBytes());
    int[] read = {0};
    filter.doFilter(
        req,
        new MockHttpServletResponse(),
        (request, response) -> {
          int c;
          var reader = request.getReader();
          while ((c = reader.read()) != -1) {
            read[0]++;
          }
        });
    assertThat(read[0]).isEqualTo("hello".length());
  }
}
