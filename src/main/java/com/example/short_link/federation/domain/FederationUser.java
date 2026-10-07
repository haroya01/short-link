package com.example.short_link.federation.domain;

public record FederationUser(
    Long id, String username, String bio, String avatarUrl, String displayName) {

  public FederationUser(Long id, String username, String bio, String avatarUrl) {
    this(id, username, bio, avatarUrl, null);
  }
}
