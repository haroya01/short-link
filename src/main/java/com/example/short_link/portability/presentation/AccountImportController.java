package com.example.short_link.portability.presentation;

import com.example.short_link.portability.application.AccountImportService;
import com.example.short_link.portability.presentation.request.StartImportRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/imports")
@RequiredArgsConstructor
public class AccountImportController {

  private final AccountImportService imports;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AccountImportService.ImportView start(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody StartImportRequest request) {
    return imports.start(userId, request.kind(), request.csv());
  }

  @GetMapping
  public List<AccountImportService.ImportView> recent(@AuthenticationPrincipal Long userId) {
    return imports.recent(userId);
  }
}
