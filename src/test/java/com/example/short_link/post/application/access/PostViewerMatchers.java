package com.example.short_link.post.application.access;

import static org.mockito.ArgumentMatchers.argThat;

import java.util.Objects;

public final class PostViewerMatchers {

  private PostViewerMatchers() {}

  public static PostViewer viewer(Long viewerId) {
    return argThat(reader -> reader != null && Objects.equals(reader.viewerId(), viewerId));
  }
}
