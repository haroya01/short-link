package com.example.short_link.note.domain;

import java.time.Instant;

public record RemotePoolRow(
    Long id, Long remoteActorId, String domain, String language, Instant createdAt, String uri) {}
