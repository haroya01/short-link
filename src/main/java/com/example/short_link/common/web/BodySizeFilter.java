package com.example.short_link.common.web;

import com.example.short_link.common.web.response.ProblemDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class BodySizeFilter extends OncePerRequestFilter {

  private static final long DEFAULT_MAX_BODY_BYTES = 16L * 1024L;

  /** Whole editor documents and bulk CSV imports need a larger cap than ordinary API payloads. */
  private static final List<Limit> EXPANDED_LIMITS =
      List.of(
          new Limit("/api/v1/posts", 1024L * 1024L),
          new Limit("/api/v1/links/bulk", 1024L * 1024L));

  private record Limit(String pathPrefix, long maxBytes) {}

  private final JsonMapper jsonMapper;

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    long limit = limitFor(req.getRequestURI());
    long contentLength = req.getContentLengthLong();
    if (contentLength > limit) {
      writeTooLarge(req, res, limit);
      return;
    }
    // Content-Length may be absent or understated; also cap the actual stream (overrun maps to
    // 413).
    chain.doFilter(new LimitedBodyRequest(req, limit), res);
  }

  private static long limitFor(String requestUri) {
    for (Limit limit : EXPANDED_LIMITS) {
      if (requestUri.startsWith(limit.pathPrefix())) {
        return limit.maxBytes();
      }
    }
    return DEFAULT_MAX_BODY_BYTES;
  }

  private void writeTooLarge(HttpServletRequest req, HttpServletResponse res, long limit)
      throws IOException {
    ProblemDetail body =
        ProblemDetails.of(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "request body exceeds " + (limit / 1024) + "KB limit",
            "PAYLOAD_TOO_LARGE",
            req);

    res.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(res.getOutputStream(), body);
  }

  private static final class LimitedBodyRequest extends HttpServletRequestWrapper {
    private final long limit;

    LimitedBodyRequest(HttpServletRequest request, long limit) {
      super(request);
      this.limit = limit;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      ServletInputStream delegate = super.getInputStream();
      long cap = limit;
      return new ServletInputStream() {
        private long count;

        @Override
        public int read() throws IOException {
          int b = delegate.read();
          if (b != -1 && ++count > cap) {
            throw new PayloadTooLargeException(cap);
          }
          return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
          int n = delegate.read(buf, off, len);
          if (n > 0) {
            count += n;
            if (count > cap) {
              throw new PayloadTooLargeException(cap);
            }
          }
          return n;
        }

        @Override
        public boolean isFinished() {
          return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
          return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
          delegate.setReadListener(listener);
        }
      };
    }

    @Override
    public BufferedReader getReader() throws IOException {
      String enc = getCharacterEncoding();
      return new BufferedReader(
          new InputStreamReader(
              getInputStream(), enc != null ? enc : StandardCharsets.UTF_8.name()));
    }
  }
}
