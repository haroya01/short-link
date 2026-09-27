package com.example.short_link.event.application;

import java.util.List;
import java.util.Optional;

public interface RegistrationClickLookup {

  record ClickSnapshot(
      Long linkId, String sourceChannel, String clientApp, String referrerHost, String utmSource) {}

  record LinkVisitor(Long linkId, String visitorHash) {}

  Optional<ClickSnapshot> findLatestHumanClick(List<LinkVisitor> candidates);
}
