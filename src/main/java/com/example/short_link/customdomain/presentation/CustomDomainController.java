package com.example.short_link.customdomain.presentation;

import com.example.short_link.customdomain.application.dto.DomainSummary;
import com.example.short_link.customdomain.application.read.CustomDomainQueryService;
import com.example.short_link.customdomain.application.write.DeleteCustomDomainUseCase;
import com.example.short_link.customdomain.application.write.RegisterCustomDomainUseCase;
import com.example.short_link.customdomain.application.write.VerifyCustomDomainUseCase;
import com.example.short_link.customdomain.presentation.request.CustomDomainRegisterRequest;
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
@RequestMapping("/api/v1/custom-domains")
@RequiredArgsConstructor
public class CustomDomainController {

  private final CustomDomainQueryService queryService;
  private final RegisterCustomDomainUseCase register;
  private final VerifyCustomDomainUseCase verify;
  private final DeleteCustomDomainUseCase delete;

  @GetMapping
  public List<DomainSummary> list(@AuthenticationPrincipal Long userId) {
    return queryService.list(userId);
  }

  @PostMapping
  public ResponseEntity<DomainSummary> register(
      @AuthenticationPrincipal Long userId,
      @Valid @RequestBody CustomDomainRegisterRequest request) {
    DomainSummary saved = register.execute(userId, request.domain());
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
  }

  @PostMapping("/{id}/verify")
  public DomainSummary verify(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return verify.execute(userId, id);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    delete.execute(userId, id);
    return ResponseEntity.noContent().build();
  }
}
