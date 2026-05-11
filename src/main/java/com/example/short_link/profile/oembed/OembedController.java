package com.example.short_link.profile.oembed;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/oembed")
@RequiredArgsConstructor
public class OembedController {

  private final OembedService service;

  @GetMapping
  public ResponseEntity<OembedResponse> get(@RequestParam("url") String url) {
    OembedResponse body = service.fetch(url);
    if (body == null) {
      return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
    }
    return ResponseEntity.ok(body);
  }
}
