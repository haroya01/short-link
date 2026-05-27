package com.example.short_link.user.presentation;

import com.example.short_link.user.application.write.ApiKeyService;
import com.example.short_link.user.application.write.ApiKeyService.ApiKeySummary;
import com.example.short_link.user.application.write.ApiKeyService.IssuedApiKey;
import com.example.short_link.user.presentation.request.ApiKeyCreateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

  private final ApiKeyService service;

  @PostMapping
  public ResponseEntity<IssuedApiKey> issue(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody ApiKeyCreateRequest request) {
    IssuedApiKey issued = service.issue(userId, request.name());
    return ResponseEntity.status(HttpStatus.CREATED).body(issued);
  }

  @GetMapping
  public List<ApiKeySummary> list(@AuthenticationPrincipal Long userId) {
    return service.list(userId);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> revoke(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    boolean removed = service.revoke(userId, id);
    return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }
}
