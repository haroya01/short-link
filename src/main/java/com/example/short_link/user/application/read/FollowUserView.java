package com.example.short_link.user.application.read;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code followedByMe} is false for anonymous viewers. A listed author's hidden followerCount is
 * null and omitted from JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FollowUserView(
    Long id,
    String username,
    String bio,
    String avatarUrl,
    Long followerCount,
    boolean followedByMe) {}
