package com.example.short_link.link.api;

import com.example.short_link.link.application.LinkService;
import com.example.short_link.link.domain.LinkEntity;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

  private final LinkService service;
  private final String baseUrl;

  public LinkController(LinkService service, @Value("${short-link.base-url}") String baseUrl) {
    this.service = service;
    this.baseUrl = baseUrl;
  }

  @PostMapping
  public ResponseEntity<CreateLinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
    LinkEntity link = service.create(request.url());
    String shortUrl = baseUrl + "/" + link.getShortCode();
    return ResponseEntity.created(URI.create(shortUrl))
        .body(new CreateLinkResponse(link.getShortCode(), shortUrl));
  }
}
