package com.example.short_link.campaign.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MissingPostEndDestinationException extends RuntimeException {
  public MissingPostEndDestinationException() {
    super("postEndDestinationUrl is required when postEndAction is REDIRECT");
  }
}
