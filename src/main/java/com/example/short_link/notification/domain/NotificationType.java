package com.example.short_link.notification.domain;

/**
 * REPLY targets the thread author, distinct from the post-owner COMMENT notice. MENTION is
 * deduplicated against COMMENT/REPLY. CONNECTED targets the connected work's author; PATH_GREW
 * targets prior contributors excluding that author and the curator. Both graph notices open the
 * collection.
 */
public enum NotificationType {
  LIKE,
  COMMENT,
  FOLLOW,
  SERIES_SUBSCRIBE,
  REPLY,
  NEW_POST,
  MENTION,
  CONNECTED,
  PATH_GREW
}
