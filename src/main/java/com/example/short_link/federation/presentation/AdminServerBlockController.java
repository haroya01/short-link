package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.ServerBlocks;
import com.example.short_link.federation.presentation.request.ServerBlockRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/federation/servers")
@RequiredArgsConstructor
public class AdminServerBlockController {

  private final ServerBlocks serverBlocks;

  @GetMapping
  public List<ServerBlocks.View> list() {
    return serverBlocks.list();
  }

  @PutMapping("/{domain:.+}")
  public ServerBlocks.View block(
      @PathVariable String domain, @Valid @RequestBody ServerBlockRequest request) {
    return serverBlocks.block(domain, request.severity(), request.reason());
  }

  @DeleteMapping("/{domain:.+}")
  public ResponseEntity<Void> unblock(@PathVariable String domain) {
    serverBlocks.unblock(domain);
    return ResponseEntity.noContent().build();
  }
}
