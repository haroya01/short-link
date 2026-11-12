package com.example.short_link.link.access.presentation;

import com.example.short_link.common.exception.GuardUnavailableException;
import com.example.short_link.common.observability.OutcomeResolver;
import com.example.short_link.link.redirect.presentation.helper.LinkHtmlRenderer;
import com.example.short_link.link.redirect.presentation.helper.VisitorLocale;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(assignableTypes = PasswordUnlockController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class PasswordUnlockExceptionHandler {

  private final LinkHtmlRenderer html;

  @ExceptionHandler(GuardUnavailableException.class)
  public ResponseEntity<byte[]> unavailable(HttpServletRequest req) {
    req.setAttribute(OutcomeResolver.ATTRIBUTE, "unavailable");
    return html.unavailablePageResponse(VisitorLocale.resolve(req.getHeader("Accept-Language")));
  }
}
