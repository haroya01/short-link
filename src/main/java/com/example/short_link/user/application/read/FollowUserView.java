package com.example.short_link.user.application.read;

/**
 * One row of a followers / following list — public author info plus the viewer's own follow state
 * (so the client can render a Follow / Following button per row). {@code followedByMe} is false for
 * anonymous viewers.
 */
public record FollowUserView(
    Long id,
    String username,
    String bio,
    String avatarUrl,
    long followerCount,
    boolean followedByMe) {}
