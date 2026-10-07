package com.example.short_link.note.domain;

import java.time.Instant;

public record RemoteNoteRow(
    Long remoteActorId,
    String uri,
    String url,
    String body,
    Instant createdAt,
    String contentWarning,
    boolean sensitive,
    NoteVisibility visibility,
    Long inReplyToId) {}
