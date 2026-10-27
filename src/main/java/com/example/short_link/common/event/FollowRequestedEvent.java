package com.example.short_link.common.event;

// Someone asked to follow a locked member: another member or an account on another server.
public record FollowRequestedEvent(Long targetUserId, Actor follower) {}
