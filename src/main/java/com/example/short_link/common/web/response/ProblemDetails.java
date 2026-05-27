package com.example.short_link.common.web.response;

import com.example.short_link.common.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * ProblemDetail 빌드 공통 util. 각 feature 의 ExceptionHandler 가 공유 — common 이 feature exception 을 import
 * 하지 않고 각 feature 가 자기 exception 만 처리하도록.
 */
public final class ProblemDetails {

  private ProblemDetails() {}

  public static ProblemDetail of(
      HttpStatus status, String detail, String code, HttpServletRequest req) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setInstance(URI.create(req.getRequestURI()));
    p.setProperty("code", code);
    p.setProperty("timestamp", Instant.now().toString());
    String requestId = MDC.get("requestId");
    if (requestId != null) {
      p.setProperty("requestId", requestId);
    }
    return p;
  }

  public static ProblemDetail of(DomainException exception, HttpServletRequest req) {
    String detail = exception instanceof Throwable t ? t.getMessage() : exception.code();
    ProblemDetail p = of(exception.status(), detail, exception.code(), req);
    exception.properties().forEach(p::setProperty);
    return p;
  }
}
