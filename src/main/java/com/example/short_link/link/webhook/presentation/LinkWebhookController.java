package com.example.short_link.link.webhook.presentation;

import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.webhook.application.dto.IssuedWebhook;
import com.example.short_link.link.webhook.application.dto.WebhookSummary;
import com.example.short_link.link.webhook.application.read.LinkWebhookQueryService;
import com.example.short_link.link.webhook.application.write.DeleteLinkWebhookCommand;
import com.example.short_link.link.webhook.application.write.DeleteLinkWebhookUseCase;
import com.example.short_link.link.webhook.application.write.RegisterLinkWebhookCommand;
import com.example.short_link.link.webhook.application.write.RegisterLinkWebhookUseCase;
import com.example.short_link.link.webhook.application.write.ToggleLinkWebhookCommand;
import com.example.short_link.link.webhook.application.write.ToggleLinkWebhookUseCase;
import com.example.short_link.link.webhook.application.write.UpdateLinkWebhookConfigCommand;
import com.example.short_link.link.webhook.application.write.UpdateLinkWebhookConfigUseCase;
import com.example.short_link.link.webhook.presentation.request.LinkWebhookConfigRequest;
import com.example.short_link.link.webhook.presentation.request.LinkWebhookRegisterRequest;
import com.example.short_link.link.webhook.presentation.request.LinkWebhookToggleRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkWebhookController {

  private final LinkWebhookQueryService queryService;
  private final RegisterLinkWebhookUseCase registerUseCase;
  private final ToggleLinkWebhookUseCase toggleUseCase;
  private final UpdateLinkWebhookConfigUseCase updateConfigUseCase;
  private final DeleteLinkWebhookUseCase deleteUseCase;

  @GetMapping("/{shortCode}/webhooks")
  public List<WebhookSummary> list(
      @AuthenticationPrincipal Long userId, @PathVariable ShortCode shortCode) {
    return queryService.list(userId, shortCode);
  }

  @PostMapping("/{shortCode}/webhooks")
  public ResponseEntity<IssuedWebhook> register(
      @AuthenticationPrincipal Long userId,
      @PathVariable ShortCode shortCode,
      @Valid @RequestBody LinkWebhookRegisterRequest request) {
    IssuedWebhook issued =
        registerUseCase.execute(
            new RegisterLinkWebhookCommand(userId, shortCode, request.url(), request.name()));
    return ResponseEntity.status(HttpStatus.CREATED).body(issued);
  }

  @PatchMapping("/{shortCode}/webhooks/{id}")
  public WebhookSummary toggle(
      @AuthenticationPrincipal Long userId,
      @PathVariable ShortCode shortCode,
      @PathVariable Long id,
      @Valid @RequestBody LinkWebhookToggleRequest request) {
    return toggleUseCase.execute(
        new ToggleLinkWebhookCommand(userId, shortCode, id, request.enabled()));
  }

  @PutMapping("/{shortCode}/webhooks/{id}/config")
  public WebhookSummary updateConfig(
      @AuthenticationPrincipal Long userId,
      @PathVariable ShortCode shortCode,
      @PathVariable Long id,
      @Valid @RequestBody LinkWebhookConfigRequest request) {
    return updateConfigUseCase.execute(
        new UpdateLinkWebhookConfigCommand(
            userId,
            shortCode,
            id,
            request.includeBots(),
            request.sampleRate(),
            request.batchEnabled(),
            request.dailyQuota(),
            request.referrerHostFilter(),
            request.utmSourceFilter(),
            request.deliveryMode(),
            request.summaryHourOfDay(),
            request.spikeThreshold(),
            request.spikeWindowMinutes()));
  }

  @DeleteMapping("/{shortCode}/webhooks/{id}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal Long userId,
      @PathVariable ShortCode shortCode,
      @PathVariable Long id) {
    deleteUseCase.execute(new DeleteLinkWebhookCommand(userId, shortCode, id));
    return ResponseEntity.noContent().build();
  }
}
