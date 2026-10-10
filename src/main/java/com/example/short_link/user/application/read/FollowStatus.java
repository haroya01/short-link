package com.example.short_link.user.application.read;

import com.example.short_link.common.user.BlockRelation;
import com.fasterxml.jackson.annotation.JsonInclude;

// Hidden follower/following totals are null and omitted from JSON. hideFollowerCount is always
// present so clients can distinguish hidden counts from zero. requested is a follow waiting on a
// locked account's approval.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FollowStatus(
    boolean following,
    Long followerCount,
    Long followingCount,
    boolean hideFollowerCount,
    boolean notifyNotes,
    boolean requested,
    boolean locked,
    boolean blockedByViewer,
    boolean blocksViewer) {

  public static FollowStatus visible(boolean following, long followerCount, long followingCount) {
    return new FollowStatus(
        following, followerCount, followingCount, false, false, false, false, false, false);
  }

  public static FollowStatus hidden(boolean following) {
    return new FollowStatus(following, null, null, true, false, false, false, false, false);
  }

  public FollowStatus notifyingOfNotes(boolean on) {
    return new FollowStatus(
        following,
        followerCount,
        followingCount,
        hideFollowerCount,
        on,
        requested,
        locked,
        blockedByViewer,
        blocksViewer);
  }

  public FollowStatus requesting(boolean requested, boolean locked) {
    return new FollowStatus(
        following,
        followerCount,
        followingCount,
        hideFollowerCount,
        notifyNotes,
        requested,
        locked,
        blockedByViewer,
        blocksViewer);
  }

  public FollowStatus blocking(BlockRelation blocks) {
    return new FollowStatus(
        following,
        followerCount,
        followingCount,
        hideFollowerCount,
        notifyNotes,
        requested,
        locked,
        blocks.blockedByViewer(),
        blocks.blocksViewer());
  }
}
