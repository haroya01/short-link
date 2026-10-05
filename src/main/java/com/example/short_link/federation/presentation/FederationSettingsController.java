package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.FederationSettings;
import com.example.short_link.federation.application.FederationSettingsView;
import com.example.short_link.federation.presentation.request.UpdateFederationSettingsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/federation/settings")
@RequiredArgsConstructor
public class FederationSettingsController {

  private final FederationSettings settings;

  @GetMapping
  public FederationSettingsView get(@AuthenticationPrincipal Long userId) {
    return settings.view(userId);
  }

  @PutMapping
  public FederationSettingsView update(
      @AuthenticationPrincipal Long userId, @RequestBody UpdateFederationSettingsRequest request) {
    return settings.update(userId, request.enabled(), request.noticeSeen());
  }
}
