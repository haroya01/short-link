package com.example.short_link.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.admin.application.InvalidActivePeriodException;
import com.example.short_link.link.application.BulkImportTooLargeException;
import com.example.short_link.link.application.DuplicateShortCodeException;
import com.example.short_link.link.application.DuplicateTagNameException;
import com.example.short_link.link.application.InvalidCursorException;
import com.example.short_link.link.application.InvalidExportDimensionException;
import com.example.short_link.link.application.InvalidWebhookUrlException;
import com.example.short_link.link.application.LinkExpiredException;
import com.example.short_link.link.application.LinkNotFoundException;
import com.example.short_link.link.application.LinkNotOwnedException;
import com.example.short_link.link.application.LinkQuotaExceededException;
import com.example.short_link.link.application.LinkViewLimitExceededException;
import com.example.short_link.link.application.MaliciousUrlException;
import com.example.short_link.link.application.ReservedShortCodeException;
import com.example.short_link.link.application.ShortCodeGenerationException;
import com.example.short_link.link.application.TagNotFoundException;
import com.example.short_link.link.application.TooManyWebhooksException;
import com.example.short_link.link.application.WebhookNotFoundException;
import com.example.short_link.profile.application.InvalidUsernameException;
import com.example.short_link.profile.application.ProfileNotFoundException;
import com.example.short_link.profile.application.UsernameTakenException;
import com.example.short_link.user.application.InvalidRefreshTokenException;
import com.example.short_link.user.application.InvalidTimezoneException;
import com.example.short_link.user.application.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerExtendedTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
  private HttpServletRequest req;

  @BeforeEach
  void setUp() {
    req = mock(HttpServletRequest.class);
    when(req.getRequestURI()).thenReturn("/api/v1/links");
    MDC.put("requestId", "req-1");
  }

  @Test
  void linkNotFound404() {
    ProblemDetail p = handler.handleLinkNotFound(new LinkNotFoundException("abc"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(p.getProperties()).containsEntry("code", "LINK_NOT_FOUND");
    assertThat(p.getProperties()).containsEntry("requestId", "req-1");
  }

  @Test
  void linkExpired410() {
    ProblemDetail p = handler.handleLinkExpired(new LinkExpiredException("abc"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.GONE.value());
    assertThat(p.getProperties()).containsEntry("code", "LINK_EXPIRED");
  }

  @Test
  void duplicateShortCode409() {
    ProblemDetail p = handler.handleDuplicateShortCode(new DuplicateShortCodeException("abc"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
  }

  @Test
  void linkNotOwned403() {
    ProblemDetail p = handler.handleLinkNotOwned(new LinkNotOwnedException("abc"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void maliciousUrl400() {
    ProblemDetail p = handler.handleMaliciousUrl(new MaliciousUrlException("http://x"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(p.getDetail()).isEqualTo("url flagged as malicious");
  }

  @Test
  void reservedShortCode400() {
    ProblemDetail p = handler.handleReservedShortCode(new ReservedShortCodeException("api"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void linkQuotaCarriesLimit() {
    ProblemDetail p = handler.handleLinkQuota(new LinkQuotaExceededException(100L), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(p.getProperties()).containsEntry("limit", 100L);
  }

  @Test
  void viewLimit410() {
    ProblemDetail p = handler.handleViewLimit(new LinkViewLimitExceededException("abc"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.GONE.value());
  }

  @Test
  void tagNotFound404() {
    ProblemDetail p = handler.handleTagNotFound(new TagNotFoundException(1L), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  void duplicateTag409() {
    ProblemDetail p = handler.handleDuplicateTag(new DuplicateTagNameException("blog"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
  }

  @Test
  void illegalArgument400() {
    ProblemDetail p = handler.handleIllegalArgument(new IllegalArgumentException("nope"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void invalidWebhookUrl400() {
    ProblemDetail p = handler.handleInvalidWebhookUrl(new InvalidWebhookUrlException(), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void tooManyWebhooks409WithLimit() {
    ProblemDetail p = handler.handleTooManyWebhooks(new TooManyWebhooksException(3), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(p.getProperties()).containsEntry("limit", 3);
  }

  @Test
  void webhookNotFound404() {
    ProblemDetail p = handler.handleWebhookNotFound(new WebhookNotFoundException(), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  void bulkImportTooLarge413WithMetadata() {
    ProblemDetail p = handler.handleBulkTooLarge(new BulkImportTooLargeException(2000, 1000), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
    assertThat(p.getProperties()).containsEntry("limit", 1000).containsEntry("rows", 2000);
  }

  @Test
  void optimisticLock409() {
    ProblemDetail p = handler.handleOptimisticLock(new OptimisticLockingFailureException("x"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
  }

  @Test
  void invalidRefresh401() {
    ProblemDetail p = handler.handleInvalidRefresh(new InvalidRefreshTokenException(), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  void userNotFound404() {
    ProblemDetail p = handler.handleUserNotFound(new UserNotFoundException(), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  void invalidCursor400() {
    ProblemDetail p = handler.handleInvalidCursor(new InvalidCursorException(), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void invalidExportDimension400() {
    ProblemDetail p =
        handler.handleInvalidExportDimension(new InvalidExportDimensionException("x"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void invalidTimezone400() {
    ProblemDetail p =
        handler.handleInvalidTimezone(new InvalidTimezoneException("Not/A/Zone"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void invalidActivePeriod400() {
    ProblemDetail p =
        handler.handleInvalidActivePeriod(new InvalidActivePeriodException("bad"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void noResource404() {
    ProblemDetail p = handler.handleNoResource(mock(NoResourceFoundException.class), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  void notReadable400() {
    ProblemDetail p = handler.handleNotReadable(mock(HttpMessageNotReadableException.class), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void shortCodeExhausted500() {
    ProblemDetail p = handler.handleShortCodeExhausted(new ShortCodeGenerationException(), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
  }

  @Test
  void invalidUsername400() {
    ProblemDetail p = handler.handleInvalidUsername(new InvalidUsernameException("bad"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
  }

  @Test
  void usernameTaken409() {
    ProblemDetail p = handler.handleUsernameTaken(new UsernameTakenException("alice"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
  }

  @Test
  void profileNotFound404() {
    ProblemDetail p = handler.handleProfileNotFound(new ProfileNotFoundException("alice"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  void unknownExceptionMaps500() {
    when(req.getMethod()).thenReturn("POST");
    ProblemDetail p = handler.handleUnknown(new RuntimeException("kaboom"), req);
    assertThat(p.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(p.getProperties()).containsEntry("code", "INTERNAL_ERROR");
  }
}
