package com.example.short_link.common.api;

import com.example.short_link.admin.application.InvalidActivePeriodException;
import com.example.short_link.common.pow.PowRequiredException;
import com.example.short_link.link.application.BulkImportTooLargeException;
import com.example.short_link.link.application.CustomDomainNotFoundException;
import com.example.short_link.link.application.CustomDomainNotVerifiedException;
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
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(LinkNotFoundException.class)
  public ProblemDetail handleLinkNotFound(LinkNotFoundException e, HttpServletRequest req) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage(), "LINK_NOT_FOUND", req);
  }

  @ExceptionHandler(LinkExpiredException.class)
  public ProblemDetail handleLinkExpired(LinkExpiredException e, HttpServletRequest req) {
    return problem(HttpStatus.GONE, e.getMessage(), "LINK_EXPIRED", req);
  }

  @ExceptionHandler(DuplicateShortCodeException.class)
  public ProblemDetail handleDuplicateShortCode(
      DuplicateShortCodeException e, HttpServletRequest req) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), "DUPLICATE_SHORT_CODE", req);
  }

  @ExceptionHandler(LinkNotOwnedException.class)
  public ProblemDetail handleLinkNotOwned(LinkNotOwnedException e, HttpServletRequest req) {
    return problem(HttpStatus.FORBIDDEN, e.getMessage(), "LINK_NOT_OWNED", req);
  }

  @ExceptionHandler(MaliciousUrlException.class)
  public ProblemDetail handleMaliciousUrl(MaliciousUrlException e, HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, "url flagged as malicious", "MALICIOUS_URL", req);
  }

  @ExceptionHandler(ReservedShortCodeException.class)
  public ProblemDetail handleReservedShortCode(
      ReservedShortCodeException e, HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "RESERVED_SHORT_CODE", req);
  }

  @ExceptionHandler(LinkQuotaExceededException.class)
  public ProblemDetail handleLinkQuota(LinkQuotaExceededException e, HttpServletRequest req) {
    ProblemDetail body = problem(HttpStatus.CONFLICT, e.getMessage(), "LINK_QUOTA_EXCEEDED", req);
    body.setProperty("limit", e.getLimit());
    return body;
  }

  @ExceptionHandler(LinkViewLimitExceededException.class)
  public ProblemDetail handleViewLimit(LinkViewLimitExceededException e, HttpServletRequest req) {
    return problem(HttpStatus.GONE, e.getMessage(), "LINK_VIEW_LIMIT_EXCEEDED", req);
  }

  @ExceptionHandler(TagNotFoundException.class)
  public ProblemDetail handleTagNotFound(TagNotFoundException e, HttpServletRequest req) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage(), "TAG_NOT_FOUND", req);
  }

  @ExceptionHandler(DuplicateTagNameException.class)
  public ProblemDetail handleDuplicateTag(DuplicateTagNameException e, HttpServletRequest req) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), "DUPLICATE_TAG_NAME", req);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail handleIllegalArgument(IllegalArgumentException e, HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_ARGUMENT", req);
  }

  @ExceptionHandler(InvalidWebhookUrlException.class)
  public ProblemDetail handleInvalidWebhookUrl(
      InvalidWebhookUrlException e, HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_WEBHOOK_URL", req);
  }

  @ExceptionHandler(TooManyWebhooksException.class)
  public ProblemDetail handleTooManyWebhooks(TooManyWebhooksException e, HttpServletRequest req) {
    ProblemDetail body = problem(HttpStatus.CONFLICT, e.getMessage(), "TOO_MANY_WEBHOOKS", req);
    body.setProperty("limit", e.getLimit());
    return body;
  }

  @ExceptionHandler(WebhookNotFoundException.class)
  public ProblemDetail handleWebhookNotFound(WebhookNotFoundException e, HttpServletRequest req) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage(), "WEBHOOK_NOT_FOUND", req);
  }

  @ExceptionHandler(BulkImportTooLargeException.class)
  public ProblemDetail handleBulkTooLarge(BulkImportTooLargeException e, HttpServletRequest req) {
    ProblemDetail body =
        problem(HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage(), "BULK_IMPORT_TOO_LARGE", req);
    body.setProperty("limit", e.getLimit());
    body.setProperty("rows", e.getRows());
    return body;
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLock(
      OptimisticLockingFailureException e, HttpServletRequest req) {
    return problem(
        HttpStatus.CONFLICT, "concurrent modification, please retry", "OPTIMISTIC_LOCK", req);
  }

  @ExceptionHandler(InvalidRefreshTokenException.class)
  public ProblemDetail handleInvalidRefresh(
      InvalidRefreshTokenException e, HttpServletRequest req) {
    return problem(HttpStatus.UNAUTHORIZED, e.getMessage(), "INVALID_REFRESH_TOKEN", req);
  }

  /**
   * PoW gate fires when an anonymous request reaches the link-create endpoint without the
   * proof-of-work headers. The exception already carries {@code @ResponseStatus(UNAUTHORIZED)} but
   * the catch-all {@code Exception} handler below wins ordering and overrides it to 500 — declaring
   * an explicit handler restores the intended 401 and gives the frontend a stable error code
   * ({@code POW_REQUIRED}) so it can clear an expired token and re-shorten on the anonymous path
   * with a fresh PoW token.
   */
  @ExceptionHandler(PowRequiredException.class)
  public ProblemDetail handlePowRequired(PowRequiredException e, HttpServletRequest req) {
    return problem(HttpStatus.UNAUTHORIZED, e.getMessage(), "POW_REQUIRED", req);
  }

  @ExceptionHandler(UserNotFoundException.class)
  public ProblemDetail handleUserNotFound(UserNotFoundException e, HttpServletRequest req) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage(), "USER_NOT_FOUND", req);
  }

  @ExceptionHandler(InvalidCursorException.class)
  public ProblemDetail handleInvalidCursor(InvalidCursorException e, HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_CURSOR", req);
  }

  @ExceptionHandler(InvalidExportDimensionException.class)
  public ProblemDetail handleInvalidExportDimension(
      InvalidExportDimensionException e, HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_EXPORT_DIMENSION", req);
  }

  @ExceptionHandler(InvalidTimezoneException.class)
  public ProblemDetail handleInvalidTimezone(InvalidTimezoneException e, HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_TIMEZONE", req);
  }

  @ExceptionHandler(InvalidActivePeriodException.class)
  public ProblemDetail handleInvalidActivePeriod(
      InvalidActivePeriodException e, HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_ACTIVE_PERIOD", req);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail handleNoResource(NoResourceFoundException e, HttpServletRequest req) {
    return problem(HttpStatus.NOT_FOUND, "resource not found", "NOT_FOUND", req);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
    ProblemDetail body =
        problem(HttpStatus.BAD_REQUEST, "validation failed", "VALIDATION_FAILED", req);
    List<Map<String, String>> errors =
        e.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    Map.of(
                        "field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
            .toList();
    body.setProperty("errors", errors);
    return body;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleNotReadable(
      HttpMessageNotReadableException e, HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, "malformed request body", "MALFORMED_REQUEST", req);
  }

  @ExceptionHandler(ShortCodeGenerationException.class)
  public ProblemDetail handleShortCodeExhausted(
      ShortCodeGenerationException e, HttpServletRequest req) {
    log.error("short code generation exhausted", e);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "short code generation exhausted",
        "SHORT_CODE_EXHAUSTED",
        req);
  }

  @ExceptionHandler(InvalidUsernameException.class)
  public ProblemDetail handleInvalidUsername(InvalidUsernameException e, HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_USERNAME", req);
  }

  @ExceptionHandler(UsernameTakenException.class)
  public ProblemDetail handleUsernameTaken(UsernameTakenException e, HttpServletRequest req) {
    return problem(HttpStatus.CONFLICT, e.getMessage(), "USERNAME_TAKEN", req);
  }

  @ExceptionHandler(ProfileNotFoundException.class)
  public ProblemDetail handleProfileNotFound(ProfileNotFoundException e, HttpServletRequest req) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage(), "PROFILE_NOT_FOUND", req);
  }

  @ExceptionHandler(CustomDomainNotFoundException.class)
  public ProblemDetail handleCustomDomainNotFound(
      CustomDomainNotFoundException e, HttpServletRequest req) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage(), "CUSTOM_DOMAIN_NOT_FOUND", req);
  }

  @ExceptionHandler(CustomDomainNotVerifiedException.class)
  public ProblemDetail handleCustomDomainNotVerified(
      CustomDomainNotVerifiedException e, HttpServletRequest req) {
    return problem(
        HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), "CUSTOM_DOMAIN_NOT_VERIFIED", req);
  }

  @ExceptionHandler(com.example.short_link.campaign.application.CampaignNotFoundException.class)
  public ProblemDetail handleCampaignNotFound(
      com.example.short_link.campaign.application.CampaignNotFoundException e,
      HttpServletRequest req) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage(), "CAMPAIGN_NOT_FOUND", req);
  }

  @ExceptionHandler(com.example.short_link.campaign.application.CampaignNotOwnedException.class)
  public ProblemDetail handleCampaignNotOwned(
      com.example.short_link.campaign.application.CampaignNotOwnedException e,
      HttpServletRequest req) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage(), "CAMPAIGN_NOT_FOUND", req);
  }

  @ExceptionHandler(
      com.example.short_link.campaign.application.InvalidCampaignPeriodException.class)
  public ProblemDetail handleInvalidCampaignPeriod(
      com.example.short_link.campaign.application.InvalidCampaignPeriodException e,
      HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_CAMPAIGN_PERIOD", req);
  }

  @ExceptionHandler(
      com.example.short_link.campaign.application.MissingPostEndDestinationException.class)
  public ProblemDetail handleMissingPostEndDestination(
      com.example.short_link.campaign.application.MissingPostEndDestinationException e,
      HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "MISSING_POST_END_DESTINATION", req);
  }

  @ExceptionHandler(com.example.short_link.campaign.application.CampaignArchivedException.class)
  public ProblemDetail handleCampaignArchived(
      com.example.short_link.campaign.application.CampaignArchivedException e,
      HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "CAMPAIGN_ARCHIVED", req);
  }

  @ExceptionHandler(
      com.example.short_link.campaign.application.CampaignTerminalStateException.class)
  public ProblemDetail handleCampaignTerminalState(
      com.example.short_link.campaign.application.CampaignTerminalStateException e,
      HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "CAMPAIGN_TERMINAL_STATE", req);
  }

  @ExceptionHandler(com.example.short_link.campaign.application.ReapplyOnNonEndedException.class)
  public ProblemDetail handleReapplyOnNonEnded(
      com.example.short_link.campaign.application.ReapplyOnNonEndedException e,
      HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "REAPPLY_ON_NON_ENDED", req);
  }

  @ExceptionHandler(
      com.example.short_link.campaign.application.CampaignBatchNotFoundException.class)
  public ProblemDetail handleCampaignBatchNotFound(
      com.example.short_link.campaign.application.CampaignBatchNotFoundException e,
      HttpServletRequest req) {
    return problem(HttpStatus.NOT_FOUND, e.getMessage(), "CAMPAIGN_BATCH_NOT_FOUND", req);
  }

  @ExceptionHandler(com.example.short_link.campaign.application.InvalidBatchRowException.class)
  public ProblemDetail handleInvalidBatchRow(
      com.example.short_link.campaign.application.InvalidBatchRowException e,
      HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_BATCH_ROW", req);
  }

  @ExceptionHandler(
      com.example.short_link.campaign.application.MissingDestinationUrlException.class)
  public ProblemDetail handleMissingDestinationUrl(
      com.example.short_link.campaign.application.MissingDestinationUrlException e,
      HttpServletRequest req) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage(), "MISSING_DESTINATION_URL", req);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnknown(Exception e, HttpServletRequest req) {
    // Method/URI are already in MDC via MdcFilter, but inlining them in the message keeps the
    // admin "recent errors" list scannable without expanding each row, and survives in plain-text
    // logs where MDC isn't part of the visible pattern.
    log.error(
        "unexpected error: {} {} ex={}",
        req.getMethod(),
        req.getRequestURI(),
        e.getClass().getSimpleName(),
        e);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "internal server error", "INTERNAL_ERROR", req);
  }

  private ProblemDetail problem(
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
}
