package com.example.short_link.common.storage;

// Every user upload lives under one of these; a hard delete empties exactly these folders.
public enum UserFolder {
  AVATARS("avatars"),
  BANNERS("banners"),
  PROFILE_IMAGES("profile-images"),
  POST_IMAGES("post-images"),
  EVENT_COVERS("event-covers"),
  NOTE_IMAGES("note-images");

  private final String folder;

  UserFolder(String folder) {
    this.folder = folder;
  }

  public String of(long userId) {
    return folder + "/" + userId + "/";
  }
}
