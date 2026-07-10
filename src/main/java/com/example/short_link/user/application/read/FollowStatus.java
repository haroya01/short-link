package com.example.short_link.user.application.read;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Follow state for a target user: whether the current viewer follows them, plus the target's
 * follower / following totals (shown on the author page).
 *
 * <p>When the author has opted to hide their counts ({@code hideFollowerCount == true}) both totals
 * are {@code null} and, thanks to {@link JsonInclude}, are dropped from the serialized response
 * altogether — a hidden count is absent, never a visible zero. The {@code hideFollowerCount} flag
 * is always present so the client can branch its rendering.
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
