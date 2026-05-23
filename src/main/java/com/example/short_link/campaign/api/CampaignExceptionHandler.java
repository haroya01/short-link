package com.example.short_link.campaign.api;

import com.example.short_link.campaign.application.CampaignArchivedException;
import com.example.short_link.campaign.application.CampaignBatchNotFoundException;
import com.example.short_link.campaign.application.CampaignNotFoundException;
import com.example.short_link.campaign.application.CampaignNotOwnedException;
import com.example.short_link.campaign.application.CampaignTerminalStateException;
import com.example.short_link.campaign.application.InvalidBatchRowException;
import com.example.short_link.campaign.application.InvalidCampaignPeriodException;
import com.example.short_link.campaign.application.MissingDestinationUrlException;
import com.example.short_link.campaign.application.MissingPostEndDestinationException;
import com.example.short_link.campaign.application.ReapplyOnNonEndedException;
import com.example.short_link.common.api.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CampaignExceptionHandler {

  @ExceptionHandler(CampaignNotFoundException.class)
  public ProblemDetail handleCampaignNotFound(CampaignNotFoundException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, e.getMessage(), "CAMPAIGN_NOT_FOUND", req);
  }

  @ExceptionHandler(CampaignNotOwnedException.class)
  public ProblemDetail handleCampaignNotOwned(CampaignNotOwnedException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, e.getMessage(), "CAMPAIGN_NOT_FOUND", req);
  }

  @ExceptionHandler(InvalidCampaignPeriodException.class)
  public ProblemDetail handleInvalidCampaignPeriod(
      InvalidCampaignPeriodException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_CAMPAIGN_PERIOD", req);
  }

  @ExceptionHandler(MissingPostEndDestinationException.class)
  public ProblemDetail handleMissingPostEndDestination(
      MissingPostEndDestinationException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.BAD_REQUEST, e.getMessage(), "MISSING_POST_END_DESTINATION", req);
  }

  @ExceptionHandler(CampaignArchivedException.class)
  public ProblemDetail handleCampaignArchived(CampaignArchivedException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, e.getMessage(), "CAMPAIGN_ARCHIVED", req);
  }

  @ExceptionHandler(CampaignTerminalStateException.class)
  public ProblemDetail handleCampaignTerminalState(
      CampaignTerminalStateException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.BAD_REQUEST, e.getMessage(), "CAMPAIGN_TERMINAL_STATE", req);
  }

  @ExceptionHandler(ReapplyOnNonEndedException.class)
  public ProblemDetail handleReapplyOnNonEnded(
      ReapplyOnNonEndedException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, e.getMessage(), "REAPPLY_ON_NON_ENDED", req);
  }

  @ExceptionHandler(CampaignBatchNotFoundException.class)
  public ProblemDetail handleCampaignBatchNotFound(
      CampaignBatchNotFoundException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, e.getMessage(), "CAMPAIGN_BATCH_NOT_FOUND", req);
  }

  @ExceptionHandler(InvalidBatchRowException.class)
  public ProblemDetail handleInvalidBatchRow(InvalidBatchRowException e, HttpServletRequest req) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_BATCH_ROW", req);
  }

  @ExceptionHandler(MissingDestinationUrlException.class)
  public ProblemDetail handleMissingDestinationUrl(
      MissingDestinationUrlException e, HttpServletRequest req) {
    return ProblemDetails.of(
        HttpStatus.BAD_REQUEST, e.getMessage(), "MISSING_DESTINATION_URL", req);
  }
}
