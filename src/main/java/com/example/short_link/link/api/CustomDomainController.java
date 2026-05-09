package com.example.short_link.link.api;

import com.example.short_link.link.application.CustomDomainService;
import com.example.short_link.link.application.CustomDomainService.DomainSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/custom-domains")
@RequiredArgsConstructor
public class CustomDomainController {

  private final CustomDomainService service;

  @GetMapping
  public List<DomainSummary> list(@AuthenticationPrincipal Long userId) {
    return service.list(userId);
  }

  @PostMapping
  public ResponseEntity<DomainSummary> register(
      @AuthenticationPrincipal Long userId, @RequestBody RegisterRequest request) {
    DomainSummary saved = service.register(userId, request.domain());
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
  }

  @PostMapping("/{id}/verify")
  public DomainSummary verify(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return service.verify(userId, id);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    service.delete(userId, id);
    return ResponseEntity.noContent().build();
  }

  public record RegisterRequest(@NotBlank @Size(max = 253) String domain) {}
}
