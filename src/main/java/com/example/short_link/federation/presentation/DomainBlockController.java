package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.DomainBlocks;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/federation/domain-blocks")
@RequiredArgsConstructor
public class DomainBlockController {

  private final DomainBlocks domainBlocks;

  @GetMapping
  public List<DomainBlocks.View> list(@AuthenticationPrincipal Long userId) {
    return domainBlocks.list(userId);
  }

  @PutMapping("/{domain:.+}")
  public DomainBlocks.View block(
      @AuthenticationPrincipal Long userId, @PathVariable String domain) {
    return domainBlocks.block(userId, domain);
  }

  @DeleteMapping("/{domain:.+}")
  public ResponseEntity<Void> unblock(
      @AuthenticationPrincipal Long userId, @PathVariable String domain) {
    domainBlocks.unblock(userId, domain);
    return ResponseEntity.noContent().build();
  }
}
