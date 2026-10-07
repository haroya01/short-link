package com.example.short_link.common.event;

// Someone asked to follow a locked member: another member (followerUserId) or an account on
// another server (followerRemoteActorId).
public record FollowRequestedEvent(
    Long targetUserId, Long followerUserId, Long followerRemoteActorId) {}
