package com.example.short_link.post.application.read;

import com.example.short_link.user.domain.UserEntity;

/** The stable user ID lets clients retain references when the username changes. */
public record PublicAuthorView(Long id, String username, String bio, String avatarUrl) {

  public static PublicAuthorView from(UserEntity user) {
    return new PublicAuthorView(
        user.getId(), user.getUsername(), user.getBio(), user.getAvatarUrl());
  }
}
