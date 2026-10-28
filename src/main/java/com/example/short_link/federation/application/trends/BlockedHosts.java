package com.example.short_link.federation.application.trends;

import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import java.util.HashMap;
import java.util.Map;

final class BlockedHosts {

  private final FederationDomainBlockRepository serverBlocks;
  private final Map<String, Boolean> asked = new HashMap<>();

  BlockedHosts(FederationDomainBlockRepository serverBlocks) {
    this.serverBlocks = serverBlocks;
  }

  boolean contains(String host) {
    return asked.computeIfAbsent(host, domain -> serverBlocks.severityFor(domain).isPresent());
  }
}
