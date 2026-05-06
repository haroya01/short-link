package com.example.short_link.link.api;

import com.example.short_link.link.application.LinkLookupService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RedirectController {

  private final LinkLookupService service;

  @GetMapping("/{shortCode:[0-9A-Za-z]{7}}")
  public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
    String originalUrl = service.findActiveOriginalUrl(shortCode);
    return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
        .location(URI.create(originalUrl))
        .build();
  }
}
