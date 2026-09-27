package com.example.short_link.notification.application.dto;

public record NotificationCollectionRef(Long collectionId, String collectionName, Long postId)
    implements NotificationTarget {
  @Override
  public String pushSubtitle() {
    return collectionName;
  }
}
