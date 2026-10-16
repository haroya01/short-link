package com.example.short_link.federation.application.trends;

import com.example.short_link.common.note.RemoteNotes;
import java.time.Instant;
import java.util.List;

// A post trending on another server, shown as it was read there and kept nowhere. uri is its
// ActivityPub id, the address /api/v1/federation/resolve takes to bring it here before anyone
// likes, boosts or answers it. Counts are the largest any server reported; servers are the ones it
// trends on.
public record TrendingNote(
    String uri,
    String body,
    Instant createdAt,
    Author author,
    List<RemoteNotes.Media> media,
    String contentWarning,
    boolean sensitive,
    String language,
    long likeCount,
    long repostCount,
    long replyCount,
    Poll poll,
    LinkPreview linkPreview,
    Remote remote,
    List<String> servers) {

  public record Author(String username, String displayName, String avatarUrl, String url) {}

  public record Poll(
      Instant expiresAt,
      boolean expired,
      boolean multiple,
      long votesCount,
      long votersCount,
      List<PollOption> options) {}

  public record PollOption(String title, long votesCount) {}

  public record LinkPreview(String url, String title, String description, String image) {}

  public record Remote(String kind, String title, String url, String community) {}

  TrendingNote on(List<String> trendingOn, long likes, long reposts, long replies) {
    return new TrendingNote(
        uri,
        body,
        createdAt,
        author,
        media,
        contentWarning,
        sensitive,
        language,
        likes,
        reposts,
        replies,
        poll,
        linkPreview,
        remote,
        trendingOn);
  }
}
