package com.example.short_link.note.domain;

import com.example.short_link.common.federation.CustomEmoji;
import com.example.short_link.common.note.NoteVisibility;
import java.time.Instant;
import java.util.List;

public record RemoteNoteRow(
    Long remoteActorId,
    String uri,
    String url,
    Instant createdAt,
    Content content,
    NoteVisibility visibility,
    Conversation conversation,
    Form form,
    Long quotedNoteId) {

  public record Content(
      String body,
      String contentWarning,
      boolean sensitive,
      String language,
      List<CustomEmoji> emojis) {}

  public record Conversation(
      Long inReplyToId, Long conversationId, boolean reply, NoteReplyPolicy replyPolicy) {}

  public record Form(String kind, String title, String community, Poll poll) {}

  public record Poll(
      List<String> options,
      Instant expiresAt,
      boolean multiple,
      Instant closedAt,
      List<Long> votes,
      long voters) {}
}
