package com.example.short_link.campaign.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MissingDestinationUrlException extends RuntimeException {
  public MissingDestinationUrlException() {
    super("destinationUrl is required when campaign default destination is not set");
  }
}
