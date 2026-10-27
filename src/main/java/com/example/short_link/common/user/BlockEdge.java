package com.example.short_link.common.user;

public record BlockEdge(Long blocker, Long blocked) {

  public static Blocker blocker(Long blocker) {
    return new Blocker(blocker);
  }

  public record Blocker(Long id) {
    public BlockEdge blocked(Long blocked) {
      return new BlockEdge(id, blocked);
    }
  }
}
