package com.example.short_link.profile.application;

import com.example.short_link.user.application.read.UserProfile;

public final class MyProfileMapper {

  private MyProfileMapper() {}

  public static MyProfile from(UserProfile user, String publicProfileBaseUrl) {
    String publicUrl = user.username() == null ? null : publicProfileBaseUrl + user.username();
    return new MyProfile(
        user.username(),
        user.bio(),
        user.profileTheme(),
        publicUrl,
        user.avatarUrl(),
        user.bannerUrl(),
        Socials.toList(user.socials()),
        user.hideFollowerCount(),
        user.displayName(),
        user.locked());
  }
}
