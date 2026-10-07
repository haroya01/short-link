package com.example.short_link.federation.domain;

import java.util.List;

public record FederationUser(
    Long id,
    String username,
    String bio,
    String avatarUrl,
    String displayName,
    List<ProfileLink> links) {

  public record ProfileLink(String channel, String url) {}

  public FederationUser(Long id, String username, String bio, String avatarUrl) {
    this(id, username, bio, avatarUrl, null, List.of());
  }

  public FederationUser(
      Long id, String username, String bio, String avatarUrl, String displayName) {
    this(id, username, bio, avatarUrl, displayName, List.of());
  }
}
