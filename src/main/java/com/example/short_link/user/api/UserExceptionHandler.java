package com.example.short_link.user.api;

import com.example.short_link.common.api.ProblemDetails;
import com.example.short_link.user.exception.InvalidRefreshTokenException;
import com.example.short_link.user.exception.InvalidTimezoneException;
import com.example.short_link.user.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserExceptionHandler {

  @ExceptionHandler(UserNotFoundException.class)
  public ProblemDetail handleUserNotFound(UserNotFoundException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, e.getMessage(), "USER_NOT_FOUND", req);
  }

  @ExceptionHandler(InvalidRefreshTokenException.class)
  public ProblemDetail handleInvalidRefresh(
      InvalidRefreshTokenException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.UNAUTHORIZED, e.getMessage(), "INVALID_REFRESH_TOKEN", req);
  }

  @ExceptionHandler(InvalidTimezoneException.class)
  public ProblemDetail handleInvalidTimezone(InvalidTimezoneException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_TIMEZONE", req);
  }
}
