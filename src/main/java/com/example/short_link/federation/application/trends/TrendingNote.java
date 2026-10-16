package com.example.short_link.federation.application.trends;

import com.example.short_link.common.note.RemoteNotes;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

// A post trending on another server, shown as it was read there and kept nowhere. It has a note's
// shape so clients draw it with their note card, with no id and no reader's marks; uri is its
// ActivityPub id, the address /api/v1/federation/resolve takes to bring it here before anyone
// likes, boosts or answers it. Counts are the largest any server reported; servers are the ones it
// trends on, sourceHost the first of them.
public record TrendingNote(
    String body,
    Instant createdAt,
    Instant editedAt,
    long likeCount,
    long repostCount,
    long replyCount,
    Author author,
    List<RemoteNotes.Media> media,
    LinkPreview linkPreview,
    String contentWarning,
    boolean sensitive,
    String language,
    Poll poll,
    Remote remote,
    String uri,
    String url,
    List<String> servers) {

  public record Author(
      Long id, String username, String avatarUrl, String displayName, Long remoteId, String url) {

    Author known(Long remoteId) {
      return new Author(-remoteId, username, avatarUrl, displayName, remoteId, url);
    }
  }

  // Votes are counted on the poll's own server: a signed-in reader sees the results, as on a
  // remote note.
  public record Poll(
      Instant expiresAt,
      boolean expired,
      boolean multiple,
      long votesCount,
      long votersCount,
      List<PollOption> options,
      Boolean voted,
      List<Integer> ownVotes) {

    Poll seenBy(boolean signedIn) {
      return new Poll(
          expiresAt,
          expired,
          multiple,
          votesCount,
          votersCount,
          options,
          signedIn ? true : null,
          signedIn ? List.of() : null);
    }
  }

  public record PollOption(String title, long votesCount) {}

  public record LinkPreview(String url, String title, String description, String image) {}

  public record Remote(String kind, String title, String url, String community) {}

  @JsonProperty
  public String sourceHost() {
    return servers.getFirst();
  }

  @JsonProperty
  public Long id() {
    return null;
  }

  @JsonProperty
  public Boolean likedByMe() {
    return null;
  }

  @JsonProperty
  public Boolean repostedByMe() {
    return null;
  }

  @JsonProperty
  public Boolean bookmarkedByMe() {
    return null;
  }

  @JsonProperty
  public Boolean conversationMuted() {
    return null;
  }

  @JsonProperty
  public Boolean canReply() {
    return null;
  }

  @JsonProperty
  public Object quotedPost() {
    return null;
  }

  @JsonProperty
  public Long inReplyToId() {
    return null;
  }

  @JsonProperty
  public Object quotedNote() {
    return null;
  }

  @JsonProperty
  public Object repostedBy() {
    return null;
  }

  @JsonProperty
  public Object thread() {
    return null;
  }

  @JsonProperty
  public long quoteCount() {
    return 0;
  }

  @JsonProperty
  public List<String> mentions() {
    return List.of();
  }

  @JsonProperty
  public boolean pinned() {
    return false;
  }

  @JsonProperty
  public boolean hidden() {
    return false;
  }

  @JsonProperty
  public String visibility() {
    return "public";
  }

  @JsonProperty
  public String replyPolicy() {
    return "everyone";
  }

  TrendingNote on(List<String> trendingOn, long likes, long reposts, long replies) {
    return new TrendingNote(
        body,
        createdAt,
        editedAt,
        likes,
        reposts,
        replies,
        author,
        media,
        linkPreview,
        contentWarning,
        sensitive,
        language,
        poll,
        remote,
        uri,
        url,
        trendingOn);
  }

  TrendingNote by(Author known) {
    return new TrendingNote(
        body,
        createdAt,
        editedAt,
        likeCount,
        repostCount,
        replyCount,
        known,
        media,
        linkPreview,
        contentWarning,
        sensitive,
        language,
        poll,
        remote,
        uri,
        url,
        servers);
  }

  TrendingNote seenBy(boolean signedIn) {
    return poll == null
        ? this
        : new TrendingNote(
            body,
            createdAt,
            editedAt,
            likeCount,
            repostCount,
            replyCount,
            author,
            media,
            linkPreview,
            contentWarning,
            sensitive,
            language,
            poll.seenBy(signedIn),
            remote,
            uri,
            url,
            servers);
  }
}
