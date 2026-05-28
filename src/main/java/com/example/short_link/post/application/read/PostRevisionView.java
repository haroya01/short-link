package com.example.short_link.post.application.read;

import com.example.short_link.post.domain.PostRevisionEntity;
import java.time.Instant;

public record PostRevisionView(
    Long id, Integer versionNumber, String titleSnapshot, Instant createdAt) {

  public static PostRevisionView from(PostRevisionEntity revision) {
    return new PostRevisionView(
        revision.getId(),
        revision.getVersionNumber(),
        revision.getTitleSnapshot(),
        revision.getCreatedAt());
  }
}
