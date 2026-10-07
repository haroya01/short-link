package com.example.short_link.profile.application;

import com.example.short_link.profile.application.Socials.Social;
import java.util.List;

public record MyProfile(
    String username,
    String bio,
    String theme,
    String publicUrl,
    String avatarUrl,
    String bannerUrl,
    List<Social> socials,
    boolean hideFollowerCount,
    String displayName) {

  public MyProfile(
      String username,
      String bio,
      String theme,
      String publicUrl,
      String avatarUrl,
      String bannerUrl,
      List<Social> socials,
      boolean hideFollowerCount) {
    this(username, bio, theme, publicUrl, avatarUrl, bannerUrl, socials, hideFollowerCount, null);
  }
}
