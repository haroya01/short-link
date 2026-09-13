package com.example.short_link.common.notification;

/**
 * Kinds checked before folding an @-mention into an existing COMMENT/REPLY notice. MENTION
 * preferences are applied separately when recording the notification.
 */
public enum BlogNotificationKind {
  COMMENT,
  REPLY
}
