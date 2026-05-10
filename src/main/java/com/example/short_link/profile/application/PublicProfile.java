package com.example.short_link.profile.application;

import java.util.List;

public record PublicProfile(String username, String bio, String theme, List<ProfileLink> links) {

  public record ProfileLink(
      String shortCode, String shortUrl, String originalUrl, String ogTitle, long clickCount) {}
}
