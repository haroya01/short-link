package com.example.short_link.user.application.read;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Hidden follower/following totals are null and omitted from JSON. {@code hideFollowerCount} is
 * always present so clients can distinguish hidden counts from zero.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FollowStatus(
    boolean following, Long followerCount, Long followingCount, boolean hideFollowerCount) {

  public static FollowStatus visible(boolean following, long followerCount, long followingCount) {
    return new FollowStatus(following, followerCount, followingCount, false);
  }

  public static FollowStatus hidden(boolean following) {
    return new FollowStatus(following, null, null, true);
  }
}
