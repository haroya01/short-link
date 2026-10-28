package com.example.short_link.note.application.read;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.QuotedPost;
import java.time.Instant;
import java.util.List;

// Counts are public and include likes and boosts from other servers. likedByMe, repostedByMe,
// bookmarkedByMe, conversationMuted and canReply are null for anonymous readers; a bookmark and a
// muted conversation are seen by no one else. replyPolicy is the thread's, set on its first note.
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
    SelfThread thread,
    String replyPolicy,
    Boolean canReply,
    boolean hidden,
    Remote remote,
    Room room) {

  public record Media(
      String url, String altText, String contentType, Integer width, Integer height) {
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
  // not vote, and on a poll from elsewhere, whose votes are cast on its own server. voted and
  // ownVotes are null for anonymous readers.
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

  // What a note from another server was there, and its address there. kind is NOTE, QUESTION,
  // ARTICLE, PAGE or VIDEO; title and community are null where the kind has none.
  public record Remote(String kind, String title, String url, String community) {}

  public record Room(Long id, String name) {}

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

  record Origin(Long id, NoteAuthor author, Instant createdAt, Instant editedAt, Remote remote) {}

  record Text(
      String body,
      List<String> mentions,
      String contentWarning,
      boolean sensitive,
      String language) {}

  record Reach(String visibility, String replyPolicy, Room room) {}

  record Standing(Long inReplyToId, boolean pinned, boolean hidden) {}

  record Attached(
      List<Media> media,
      QuotedPost quotedPost,
      QuotedNote quotedNote,
      LinkPreview linkPreview,
      Poll poll) {}

  record Counts(Long likes, long replies, Long reposts, long quotes) {}

  record Mine(
      Boolean liked,
      Boolean reposted,
      Boolean bookmarked,
      Boolean conversationMuted,
      Boolean canReply) {

    static final Mine ANONYMOUS = new Mine(null, null, null, null, null);
  }

  record InFeed(NoteAuthor repostedBy, SelfThread thread) {

    static final InFeed ALONE = new InFeed(null, null);
  }

  static NoteView compose(
      Origin origin,
      Text text,
      Reach reach,
      Standing standing,
      Attached attached,
      Counts counts,
      Mine mine,
      InFeed inFeed) {
    return new NoteView(
        origin.id(),
        text.body(),
        origin.createdAt(),
        origin.editedAt(),
        counts.likes(),
        mine.liked(),
        origin.author(),
        attached.media(),
        attached.quotedPost(),
        standing.inReplyToId(),
        counts.replies(),
        counts.reposts(),
        mine.reposted(),
        attached.quotedNote(),
        attached.linkPreview(),
        inFeed.repostedBy(),
        counts.quotes(),
        mine.bookmarked(),
        text.mentions(),
        text.contentWarning(),
        text.sensitive(),
        standing.pinned(),
        reach.visibility(),
        attached.poll(),
        mine.conversationMuted(),
        text.language(),
        inFeed.thread(),
        reach.replyPolicy(),
        mine.canReply(),
        standing.hidden(),
        origin.remote(),
        reach.room());
  }

  NoteView withRepostedBy(NoteAuthor reposter) {
    return shownAs(new InFeed(reposter, thread));
  }

  NoteView withThread(SelfThread selfThread) {
    return shownAs(new InFeed(repostedBy, selfThread));
  }

  private NoteView shownAs(InFeed inFeed) {
    return compose(
        new Origin(id, author, createdAt, editedAt, remote),
        new Text(body, mentions, contentWarning, sensitive, language),
        new Reach(visibility, replyPolicy, room),
        new Standing(inReplyToId, pinned, hidden),
        new Attached(media, quotedPost, quotedNote, linkPreview, poll),
        new Counts(likeCount, replyCount, repostCount, quoteCount),
        new Mine(likedByMe, repostedByMe, bookmarkedByMe, conversationMuted, canReply),
        inFeed);
  }
}
