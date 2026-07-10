package com.example.short_link.user.application.read;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One row of a followers / following list — public author info plus the viewer's own follow state
 * (so the client can render a Follow / Following button per row). {@code followedByMe} is false for
 * anonymous viewers.
 *
 * <p>{@code followerCount} is {@code null} — and, via {@link JsonInclude}, omitted from the row —
 * for a listed author who has opted to hide their own count.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FollowUserView(
    Long id,
    String username,
    String bio,
    String avatarUrl,
    Long followerCount,
    boolean followedByMe) {}
