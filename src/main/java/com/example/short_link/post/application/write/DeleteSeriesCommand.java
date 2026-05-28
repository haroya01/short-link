package com.example.short_link.post.application.write;

public record DeleteSeriesCommand(Long userId, Long seriesId) {

  public DeleteSeriesCommand {
    if (userId == null) throw new IllegalArgumentException("userId required");
    if (seriesId == null) throw new IllegalArgumentException("seriesId required");
  }
}
