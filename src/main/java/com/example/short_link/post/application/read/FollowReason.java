package com.example.short_link.post.application.read;

/**
 * Why a post appears in a viewer's "following" feed — the match signal, so the UI can explain "왜 이
 * 글이 떴는지". {@code kind} is AUTHOR (you follow the author), SERIES (you subscribe to its series), or
 * TOPIC (it carries a tag you follow); {@code tag} names the matched tag for TOPIC, else null. When
 * a post matches more than one signal, AUTHOR wins over SERIES over TOPIC — the most direct
 * relationship, and the least surprising to the reader.
 */
public record FollowReason(String kind, String tag) {
  public static final String AUTHOR = "AUTHOR";
  public static final String SERIES = "SERIES";
  public static final String TOPIC = "TOPIC";

  public static FollowReason author() {
    return new FollowReason(AUTHOR, null);
  }

  public static FollowReason series() {
    return new FollowReason(SERIES, null);
  }

  public static FollowReason topic(String tag) {
    return new FollowReason(TOPIC, tag);
  }
}
