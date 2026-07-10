package com.example.short_link.profile.application.write;

public record UpdateProfileCommand(
    Long userId,
    String username,
    String bio,
    String theme,
    String socials,
    Boolean hideFollowerCount) {

  public UpdateProfileCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
  }
}
