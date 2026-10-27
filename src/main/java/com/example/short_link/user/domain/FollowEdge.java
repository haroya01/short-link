package com.example.short_link.user.domain;

public record FollowEdge(Long follower, Long following) {

  public static Follower follower(Long follower) {
    return new Follower(follower);
  }

  public record Follower(Long id) {
    public FollowEdge following(Long following) {
      return new FollowEdge(id, following);
    }
  }
}
