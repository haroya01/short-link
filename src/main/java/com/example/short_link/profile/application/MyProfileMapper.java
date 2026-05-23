package com.example.short_link.profile.application;

import com.example.short_link.user.domain.UserEntity;

public final class MyProfileMapper {

  private MyProfileMapper() {}

  public static MyProfile from(UserEntity user, String publicProfileBaseUrl) {
    String publicUrl =
        user.getUsername() == null ? null : publicProfileBaseUrl + user.getUsername();
    return new MyProfile(
        user.getUsername(),
        user.getBio(),
        user.getProfileTheme(),
        publicUrl,
        user.getAvatarUrl(),
        user.getBannerUrl(),
        Socials.toList(user.getSocials()));
  }
}
