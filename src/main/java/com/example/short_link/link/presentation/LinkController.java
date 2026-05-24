package com.example.short_link.link.presentation;

import com.example.short_link.common.pow.PowRequiredException;
import com.example.short_link.common.pow.PowService;
import com.example.short_link.link.application.LinkCreated;
import com.example.short_link.link.application.LinkCreationService;
import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.presentation.request.CreateLinkRequest;
import com.example.short_link.link.presentation.response.CreateLinkResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkController {

  private final LinkCreationService service;
  private final ShortLinkUrlBuilder urlBuilder;
  private final PowService powService;

  @PostMapping
  public ResponseEntity<CreateLinkResponse> create(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody CreateLinkRequest request,
      @RequestHeader(value = "X-Pow-Challenge", required = false) String powChallenge,
      @RequestHeader(value = "X-Pow-Nonce", required = false) String powNonce) {
    if (userId == null && powService.isEnforced()) {
      if (!powService.verifyAndConsume(powChallenge, powNonce)) {
        throw new PowRequiredException();
      }
    }
    LinkCreated created =
        service.create(request.url(), userId, request.customCode(), request.expiresAt());
    String shortUrl = urlBuilder.build(created.shortCode());
    return ResponseEntity.created(URI.create(shortUrl))
        .body(new CreateLinkResponse(created.shortCode(), shortUrl, created.claimToken()));
  }
}
