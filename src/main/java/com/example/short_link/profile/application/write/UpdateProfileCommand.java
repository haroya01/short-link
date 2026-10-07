package com.example.short_link.profile.application.write;

public record UpdateProfileCommand(
    Long userId,
    String username,
    String bio,
    String theme,
    String socials,
    Boolean hideFollowerCount,
    String displayName) {

  public UpdateProfileCommand(
      Long userId,
      String username,
      String bio,
      String theme,
      String socials,
      Boolean hideFollowerCount) {
    this(userId, username, bio, theme, socials, hideFollowerCount, null);
  }

  public UpdateProfileCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
  }
}
