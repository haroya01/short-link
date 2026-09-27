package com.example.short_link.link.redirect.presentation.helper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** A page shown on the way to the destination — the click already counted as a redirect. */
public final class VisitPage extends ResponseEntity<byte[]> {

  VisitPage(byte[] body, HttpHeaders headers) {
    super(body, headers, HttpStatus.OK);
  }
}
