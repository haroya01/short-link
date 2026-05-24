package com.example.short_link.admin.presentation;

import com.example.short_link.admin.application.BlockedDomainService;
import com.example.short_link.admin.domain.BlockedDomainEntity;
import com.example.short_link.admin.presentation.request.BlockDomainRequest;
import com.example.short_link.admin.presentation.response.BlockedDomainResponse;
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
@RequestMapping("/api/v1/admin/blocked-domains")
@RequiredArgsConstructor
public class BlockedDomainController {

  private final BlockedDomainService service;

  @GetMapping
  public List<BlockedDomainResponse> list() {
    return service.list().stream().map(BlockedDomainResponse::from).toList();
  }

  @PostMapping
  public ResponseEntity<BlockedDomainResponse> block(
      @AuthenticationPrincipal Long userId, @RequestBody BlockDomainRequest request) {
    BlockedDomainEntity blocked = service.block(request.domain(), request.reason(), userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(BlockedDomainResponse.from(blocked));
  }

  @DeleteMapping("/{domain}")
  public ResponseEntity<Void> unblock(@PathVariable String domain) {
    boolean removed = service.unblock(domain);
    return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }
}
