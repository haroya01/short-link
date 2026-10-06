package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.QuotedPost;
import java.time.Instant;
import java.util.List;

// Counts are public and include likes and boosts from other servers. likedByMe, repostedByMe and
// bookmarkedByMe are null for anonymous readers; a bookmark is seen by no one else.
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
    LinkPreview linkPreview,
    NoteAuthor repostedBy,
    long quoteCount,
    Boolean bookmarkedByMe,
    List<String> mentions) {

  public NoteView(
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
    this(
        id,
        body,
        createdAt,
        editedAt,
        likeCount,
        likedByMe,
        author,
        media,
        quotedPost,
        inReplyToId,
        replyCount,
        repostCount,
        repostedByMe,
        quotedNote,
        linkPreview,
        null,
        0,
        null,
        List.of());
  }

  public record Media(String url, String altText, String contentType) {}

  public record LinkPreview(String url, String title, String description, String image) {}

  public record QuotedNote(
      Long id, String body, Instant createdAt, NoteAuthor author, List<Media> media) {}

  NoteView withRepostedBy(NoteAuthor reposter) {
    return new NoteView(
        id,
        body,
        createdAt,
        editedAt,
        likeCount,
        likedByMe,
        author,
        media,
        quotedPost,
        inReplyToId,
        replyCount,
        repostCount,
        repostedByMe,
        quotedNote,
        linkPreview,
        reposter,
        quoteCount,
        bookmarkedByMe,
        mentions);
  }
}
