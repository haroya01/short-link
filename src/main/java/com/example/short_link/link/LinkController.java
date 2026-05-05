package com.example.short_link.link;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkController {

  private final LinkService service;

  @PostMapping
  public CreateLinkResponse create(@Valid @RequestBody CreateLinkRequest request) {
    return service.create(request.url());
  }
}
