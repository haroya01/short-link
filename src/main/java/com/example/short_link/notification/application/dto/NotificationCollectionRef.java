package com.example.short_link.notification.application.dto;

/**
 * Names are snapshotted at write time. {@code collectionId} is the deep-link target; {@code postId}
 * is preview context and is null for connected notes.
 */
public record NotificationCollectionRef(Long collectionId, String collectionName, Long postId)
    implements NotificationTarget {
  @Override
  public String pushSubtitle() {
    return collectionName;
  }
}
