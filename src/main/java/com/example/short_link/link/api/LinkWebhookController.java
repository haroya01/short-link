package com.example.short_link.link.api;

import com.example.short_link.link.application.LinkWebhookService;
import com.example.short_link.link.application.LinkWebhookService.IssuedWebhook;
import com.example.short_link.link.application.LinkWebhookService.WebhookSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkWebhookController {

  private final LinkWebhookService service;

  @GetMapping("/{shortCode}/webhooks")
  public List<WebhookSummary> list(
      @AuthenticationPrincipal Long userId, @PathVariable String shortCode) {
    return service.list(userId, shortCode);
  }

  @PostMapping("/{shortCode}/webhooks")
  public ResponseEntity<IssuedWebhook> register(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @RequestBody RegisterRequest request) {
    IssuedWebhook issued = service.register(userId, shortCode, request.url(), request.name());
    return ResponseEntity.status(HttpStatus.CREATED).body(issued);
  }

  @PatchMapping("/{shortCode}/webhooks/{id}")
  public WebhookSummary toggle(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @PathVariable Long id,
      @RequestBody ToggleRequest request) {
    return service.toggle(userId, shortCode, id, request.enabled());
  }

  @DeleteMapping("/{shortCode}/webhooks/{id}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal Long userId, @PathVariable String shortCode, @PathVariable Long id) {
    service.delete(userId, shortCode, id);
    return ResponseEntity.noContent().build();
  }

  public record RegisterRequest(
      @NotBlank @Size(max = 2048) String url, @Size(max = 100) String name) {}

  public record ToggleRequest(boolean enabled) {}
}
