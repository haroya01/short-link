package com.example.short_link.user.application.read;

/**
 * Follow state for a target user: whether the current viewer follows them, plus the target's
 * follower / following totals (shown on the author page).
 */
public record FollowStatus(boolean following, long followerCount, long followingCount) {}
