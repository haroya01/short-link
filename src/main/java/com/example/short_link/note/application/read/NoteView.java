package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.QuotedPost;
import java.time.Instant;
import java.util.List;

// likeCount and repostCount are the author's own numbers; everyone else gets null (counts are not
// public). likedByMe and repostedByMe are null for anonymous readers.
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
    long replyCount,
    Long repostCount,
    Boolean repostedByMe,
    QuotedNote quotedNote,
    LinkPreview linkPreview) {

  public record Media(String url, String altText, String contentType) {}

  public record LinkPreview(String url, String title, String description, String image) {}

  public record QuotedNote(
      Long id, String body, Instant createdAt, NoteAuthor author, List<Media> media) {}

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
        replyCount,
        repostCount,
        repostedByMe,
        quotedNote,
        linkPreview);
  }
}
