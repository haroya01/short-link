package com.example.short_link.link.application.dto;

import java.time.Instant;
import java.util.List;

public record LinkEvidence(
    long linkId,
    Long ownerId,
    Introduction introduction,
    List<Destination> destinations,
    OwnerLinks ownerLinks) {

  public LinkEvidence {
    destinations = List.copyOf(destinations);
  }

  public record Introduction(
      String title, String description, String fetchStatus, Instant fetchedAt, String imageUrl) {}

  public record Destination(
      String host,
      DestinationKind kind,
      Integer weight,
      String countryCode,
      String deviceClass,
      String os) {}

  public enum DestinationKind {
    ORIGINAL,
    BRANCH,
    AFTER_EXPIRY
  }

  public record OwnerLinks(long total, long disabled) {}

  public record Image(long linkId, String url) {}
}
