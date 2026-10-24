package com.example.short_link.note.domain;

import com.example.short_link.common.note.NoteVisibility;
import java.time.Instant;
import java.util.List;

public record RemoteNoteRow(
    Long remoteActorId,
    String uri,
    String url,
    String body,
    Instant createdAt,
    String contentWarning,
    boolean sensitive,
    NoteVisibility visibility,
    Long inReplyToId,
    Long conversationId,
    boolean reply,
    NoteReplyPolicy replyPolicy,
    String language,
    String kind,
    String title,
    String community,
    Poll poll,
    Long quotedNoteId) {

  public record Poll(
      List<String> options,
      Instant expiresAt,
      boolean multiple,
      Instant closedAt,
      List<Long> votes,
      long voters) {}
}
