package com.example.short_link.user.application.read;

import com.fasterxml.jackson.annotation.JsonInclude;

// Hidden follower/following totals are null and omitted from JSON. hideFollowerCount is always
// present so clients can distinguish hidden counts from zero.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FollowStatus(
    boolean following,
    Long followerCount,
    Long followingCount,
    boolean hideFollowerCount,
    boolean notifyNotes) {

  public static FollowStatus visible(boolean following, long followerCount, long followingCount) {
    return new FollowStatus(following, followerCount, followingCount, false, false);
  }

  public static FollowStatus hidden(boolean following) {
    return new FollowStatus(following, null, null, true, false);
  }

  public FollowStatus notifyingOfNotes(boolean on) {
    return new FollowStatus(following, followerCount, followingCount, hideFollowerCount, on);
  }
}
