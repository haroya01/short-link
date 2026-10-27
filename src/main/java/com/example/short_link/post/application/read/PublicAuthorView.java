package com.example.short_link.post.application.read;

import com.example.short_link.user.application.read.UserSummary;

// The stable user ID lets clients retain references when the username changes.
public record PublicAuthorView(
    Long id, String username, String bio, String avatarUrl, String displayName) {

  public PublicAuthorView(Long id, String username, String bio, String avatarUrl) {
    this(id, username, bio, avatarUrl, null);
  }

  public static PublicAuthorView from(UserSummary user) {
    return new PublicAuthorView(
        user.id(), user.username(), user.bio(), user.avatarUrl(), user.displayName());
  }
}
