package com.example.short_link.notification.domain;

/**
 * WARNING bypasses opt-out preferences because operator policy notices must reach the account.
 * Other types have configurable push preferences.
 */
public enum LinkNotificationType {
  FIRST_CLICK,
  MILESTONE,
  VELOCITY_SPIKE,
  EXPIRY_IMMINENT,
  DIGEST,
  WARNING
}
