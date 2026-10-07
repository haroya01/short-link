package com.example.short_link.federation.domain;

public record FollowOnDomain<T>(T follow, RemoteActorEntity actor) {}
