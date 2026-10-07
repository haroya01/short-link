package com.example.short_link.common.event;

public record FollowRequestSettledEvent(
    Long targetUserId, Long followerUserId, Long followerRemoteActorId) {}
