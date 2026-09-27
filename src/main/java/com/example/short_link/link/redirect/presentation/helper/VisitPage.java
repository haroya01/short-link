package com.example.short_link.link.redirect.presentation.helper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** A visitor page whose status alone would be misread by redirect metrics, so it names its own. */
public final class VisitPage extends ResponseEntity<byte[]> {

  private final String outcome;

  VisitPage(byte[] body, HttpHeaders headers, HttpStatus status, String outcome) {
    super(body, headers, status);
    this.outcome = outcome;
  }

  public String outcome() {
    return outcome;
  }
}
