package com.example.short_link.admin.application.dto;

import java.time.Instant;
import java.util.List;

public record AdminLinkEvidence(
    EvidencePage page,
    List<EvidenceDestination> destinations,
    EvidenceHistory history,
    EvidenceCreator creator,
    EvidenceReports reports) {

  public AdminLinkEvidence {
    destinations = List.copyOf(destinations);
  }

  public record EvidencePage(
      String title,
      String description,
      String fetchStatus,
      Instant fetchedAt,
      boolean hasImage,
      boolean imageWithheld) {}

  public record EvidenceDestination(
      String host,
      String kind,
      Integer weight,
      String countryCode,
      String deviceClass,
      String os) {}

  public record EvidenceHistory(
      long retentionDays, long urlChanges, long destinationChanges, EvidenceChange latest) {}

  public record EvidenceChange(String action, Instant at, String fromHost, String toHost) {}

  public record EvidenceCreator(
      boolean anonymous,
      Long userId,
      Instant joinedAt,
      String status,
      Instant suspendedUntil,
      Long links,
      Long disabledLinks) {}

  public record EvidenceReports(long total, long open, List<EvidenceReason> reasons) {

    public EvidenceReports {
      reasons = List.copyOf(reasons);
    }
  }

  public record EvidenceReason(String reason, long count) {}
}
