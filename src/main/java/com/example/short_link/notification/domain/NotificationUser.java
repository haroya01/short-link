package com.example.short_link.notification.domain;

/** User attributes needed to localize and address notification pushes. */
public record NotificationUser(Long id, String username, String locale) {
  public String localeTag() {
    return locale == null ? "ko" : locale;
  }
}
