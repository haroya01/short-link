package com.example.short_link.profile.api;

import com.example.short_link.common.api.response.ProblemDetails;
import com.example.short_link.profile.exception.EmailLeadRateLimitedException;
import com.example.short_link.profile.exception.InvalidUsernameException;
import com.example.short_link.profile.exception.OembedNotApplicableException;
import com.example.short_link.profile.exception.ProfileNotFoundException;
import com.example.short_link.profile.exception.UsernameTakenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProfileExceptionHandler {

  @ExceptionHandler(InvalidUsernameException.class)
  public ProblemDetail handleInvalidUsername(InvalidUsernameException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_USERNAME", req);
  }

  @ExceptionHandler(UsernameTakenException.class)
  public ProblemDetail handleUsernameTaken(UsernameTakenException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.CONFLICT, e.getMessage(), "USERNAME_TAKEN", req);
  }

  @ExceptionHandler(ProfileNotFoundException.class)
  public ProblemDetail handleProfileNotFound(ProfileNotFoundException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, e.getMessage(), "PROFILE_NOT_FOUND", req);
  }

  @ExceptionHandler(OembedNotApplicableException.class)
  public ProblemDetail handleOembedNotApplicable(
      OembedNotApplicableException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), "OEMBED_NOT_APPLICABLE", req);
  }

  @ExceptionHandler(EmailLeadRateLimitedException.class)
  public ProblemDetail handleEmailLeadRateLimited(
      EmailLeadRateLimitedException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), "EMAIL_LEAD_RATE_LIMITED", req);
  }
}
