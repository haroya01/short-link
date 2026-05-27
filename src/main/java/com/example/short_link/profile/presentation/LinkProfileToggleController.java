package com.example.short_link.profile.presentation;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.profile.application.write.SetLinkHighlightCommand;
import com.example.short_link.profile.application.write.SetLinkHighlightUseCase;
import com.example.short_link.profile.application.write.ToggleLinkOnProfileCommand;
import com.example.short_link.profile.application.write.ToggleLinkOnProfileUseCase;
import com.example.short_link.profile.presentation.request.LinkProfileHighlightRequest;
import com.example.short_link.profile.presentation.request.LinkProfileToggleRequest;
import com.example.short_link.profile.presentation.response.LinkProfileHighlightResponse;
import com.example.short_link.profile.presentation.response.LinkProfileToggleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links/{shortCode}/profile")
@RequiredArgsConstructor
public class LinkProfileToggleController {

  private final ToggleLinkOnProfileUseCase toggleLinkOnProfile;
  private final SetLinkHighlightUseCase setLinkHighlight;

  @PutMapping
  public LinkProfileToggleResponse toggle(
      @AuthenticationPrincipal Long userId,
      @PathVariable ShortCode shortCode,
      @Valid @RequestBody LinkProfileToggleRequest request) {
    toggleLinkOnProfile.execute(new ToggleLinkOnProfileCommand(userId, shortCode, request.show()));
    return new LinkProfileToggleResponse(request.show());
  }

  @PutMapping("/highlight")
  public LinkProfileHighlightResponse setHighlight(
      @AuthenticationPrincipal Long userId,
      @PathVariable ShortCode shortCode,
      @Valid @RequestBody LinkProfileHighlightRequest request) {
    setLinkHighlight.execute(new SetLinkHighlightCommand(userId, shortCode, request.highlighted()));
    return new LinkProfileHighlightResponse(request.highlighted());
  }
}
