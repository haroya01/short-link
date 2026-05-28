package com.example.short_link.post.application.read;

import com.example.short_link.user.domain.UserEntity;

/**
 * Public-safe author info. id 는 abuse report subjectId 등 stable reference 위해 노출 (username 변경 가능하지만
 * id 는 영구).
 */
public record PublicAuthorView(Long id, String username, String bio, String avatarUrl) {

  public static PublicAuthorView from(UserEntity user) {
    return new PublicAuthorView(
        user.getId(), user.getUsername(), user.getBio(), user.getAvatarUrl());
  }
}
