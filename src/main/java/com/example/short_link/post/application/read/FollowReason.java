package com.example.short_link.post.application.read;

/**
 * {@code tag} is the matched tag for TOPIC, otherwise null. When signals overlap, AUTHOR takes
 * priority over SERIES, then TOPIC.
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
