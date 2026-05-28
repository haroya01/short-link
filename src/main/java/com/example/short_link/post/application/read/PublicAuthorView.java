package com.example.short_link.post.application.read;

import com.example.short_link.user.domain.UserEntity;

/** Public-safe author info — visitors 가 보는 publishing 사이트 작성자 카드. */
public record PublicAuthorView(String username, String bio, String avatarUrl) {

  public static PublicAuthorView from(UserEntity user) {
    return new PublicAuthorView(user.getUsername(), user.getBio(), user.getAvatarUrl());
  }
}
