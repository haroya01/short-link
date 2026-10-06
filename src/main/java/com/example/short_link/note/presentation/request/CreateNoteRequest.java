package com.example.short_link.note.presentation.request;

import com.example.short_link.note.application.write.NoteDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateNoteRequest(
    String body,
    @Size(max = 4) List<@Valid ImageRequest> images,
    Long quotedPostId,
    Long inReplyToId,
    Long quotedNoteId) {

  public record ImageRequest(String key, String altText) {}

  public NoteDraft toDraft() {
    return new NoteDraft(
        body,
        images == null
            ? List.of()
            : images.stream()
                .map(image -> new NoteDraft.Image(image.key(), image.altText()))
                .toList(),
        quotedPostId,
        inReplyToId,
        quotedNoteId);
  }
}
