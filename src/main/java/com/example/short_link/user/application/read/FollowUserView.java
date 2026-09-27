package com.example.short_link.user.application.read;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FollowUserView(
    Long id,
    String username,
    String bio,
    String avatarUrl,
    Long followerCount,
    boolean followedByMe) {}
