package com.example.short_link.federation.domain;

public record OnRemoteAccount<T>(T row, RemoteActorEntity actor) {}
