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
    Long quotedNoteId,
    String contentWarning,
    Boolean sensitive,
    String visibility,
    PollRequest poll,
    String language,
    String replyPolicy) {

  public record ImageRequest(String key, String altText, Integer width, Integer height) {}

  public record PollRequest(List<String> options, Long expiresIn, Boolean multiple) {}

  public NoteDraft toDraft() {
    return new NoteDraft(
        body,
        images == null
            ? List.of()
            : images.stream()
                .map(
                    image ->
                        new NoteDraft.Image(
                            image.key(), image.altText(), image.width(), image.height()))
                .toList(),
        quotedPostId,
        inReplyToId,
        quotedNoteId,
        contentWarning,
        Boolean.TRUE.equals(sensitive),
        visibility,
        poll == null
            ? null
            : new NoteDraft.Poll(
                poll.options(), poll.expiresIn(), Boolean.TRUE.equals(poll.multiple())),
        language,
        replyPolicy);
  }
}
