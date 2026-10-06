package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.QuotedPost;
import java.time.Instant;
import java.util.List;

// likeCount is the author's own number; everyone else gets null (like counts are not public).
// likedByMe is null for anonymous readers.
public record NoteView(
    Long id,
    String body,
    Instant createdAt,
    Instant editedAt,
    Long likeCount,
    Boolean likedByMe,
    NoteAuthor author,
    List<Media> media,
    QuotedPost quotedPost,
    Long inReplyToId,
    long replyCount) {

  public record Media(String url, String altText, String contentType) {}

  NoteView withLikeCount(Long count) {
    return new NoteView(
        id,
        body,
        createdAt,
        editedAt,
        count,
        likedByMe,
        author,
        media,
        quotedPost,
        inReplyToId,
        replyCount);
  }
}
