package com.example.short_link.link.api;

import com.example.short_link.link.application.LinkCreated;
import com.example.short_link.link.application.LinkCreationService;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkController {

  private final LinkCreationService service;
  private final ShortLinkUrlBuilder urlBuilder;

  @PostMapping
  public ResponseEntity<CreateLinkResponse> create(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody CreateLinkRequest request) {
    LinkCreated created = service.create(request.url(), userId);
    String shortUrl = urlBuilder.build(created.shortCode());
    return ResponseEntity.created(URI.create(shortUrl))
        .body(new CreateLinkResponse(created.shortCode(), shortUrl));
  }
}
