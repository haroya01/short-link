package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.QuotedPost;
import java.time.Instant;
import java.util.List;

// Counts are public and include likes and boosts from other servers. likedByMe, repostedByMe,
// bookmarkedByMe and conversationMuted are null for anonymous readers; a bookmark and a muted
// conversation are seen by no one else.
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
    List<String> mentions,
    String contentWarning,
    boolean sensitive,
    boolean pinned,
    String visibility,
    Poll poll,
    Boolean conversationMuted,
    String language,
    SelfThread thread) {

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
      LinkPreview linkPreview,
      NoteAuthor repostedBy,
      long quoteCount,
      Boolean bookmarkedByMe,
      List<String> mentions,
      String contentWarning,
      boolean sensitive,
      boolean pinned,
      String visibility,
      Poll poll,
      Boolean conversationMuted,
      String language) {
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
        repostedBy,
        quoteCount,
        bookmarkedByMe,
        mentions,
        contentWarning,
        sensitive,
        pinned,
        visibility,
        poll,
        conversationMuted,
        language,
        null);
  }

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
      LinkPreview linkPreview,
      NoteAuthor repostedBy,
      long quoteCount,
      Boolean bookmarkedByMe,
      List<String> mentions,
      String contentWarning,
      boolean sensitive,
      boolean pinned,
      String visibility,
      Poll poll) {
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
        repostedBy,
        quoteCount,
        bookmarkedByMe,
        mentions,
        contentWarning,
        sensitive,
        pinned,
        visibility,
        poll,
        null,
        null);
  }

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
        List.of(),
        null,
        false,
        false,
        "public",
        null);
  }

  public record Media(
      String url, String altText, String contentType, Integer width, Integer height) {
    public Media(String url, String altText, String contentType) {
      this(url, altText, contentType, null, null);
    }

    public static Media of(NoteMediaEntity image) {
      return new Media(
          image.getUrl(),
          image.getAltText(),
          image.getContentType(),
          image.getWidth(),
          image.getHeight());
    }
  }

  // Mastodon's poll: counts are public, and voted is true for the author, who sees results and does
  // not vote. voted and ownVotes are null for anonymous readers.
  public record Poll(
      Instant expiresAt,
      boolean expired,
      boolean multiple,
      long votesCount,
      long votersCount,
      List<PollOption> options,
      Boolean voted,
      List<Integer> ownVotes) {}

  public record PollOption(String title, long votesCount) {}

  public record LinkPreview(String url, String title, String description, String image) {}

  // A thread the author wrote in parts: how many parts there are, counting this one, and the parts
  // a feed shows right under it.
  public record SelfThread(int total, List<NoteView> preview) {}

  public record QuotedNote(
      Long id,
      String body,
      Instant createdAt,
      NoteAuthor author,
      List<Media> media,
      String contentWarning,
      boolean sensitive) {}

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
        mentions,
        contentWarning,
        sensitive,
        pinned,
        visibility,
        poll,
        conversationMuted,
        language,
        thread);
  }

  NoteView withThread(SelfThread selfThread) {
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
        repostedBy,
        quoteCount,
        bookmarkedByMe,
        mentions,
        contentWarning,
        sensitive,
        pinned,
        visibility,
        poll,
        conversationMuted,
        language,
        selfThread);
  }
}
