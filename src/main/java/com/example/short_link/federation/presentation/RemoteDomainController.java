package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.RemoteDomainSuggestions;
import com.example.short_link.federation.presentation.response.RemoteDomainListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/federation")
@RequiredArgsConstructor
public class RemoteDomainController {

  private final RemoteDomainSuggestions suggestions;

  @GetMapping("/domains")
  public RemoteDomainListResponse domains(
      @RequestParam(defaultValue = "") String prefix, @RequestParam(defaultValue = "5") int limit) {
    return RemoteDomainListResponse.of(suggestions.suggest(prefix, limit));
  }
}
