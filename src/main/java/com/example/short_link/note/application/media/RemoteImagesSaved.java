package com.example.short_link.note.application.media;

import java.util.List;

public record RemoteImagesSaved(List<RemoteImage> images) {

  public RemoteImagesSaved {
    images = List.copyOf(images);
  }
}
