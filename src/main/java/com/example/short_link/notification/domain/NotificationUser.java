package com.example.short_link.notification.domain;

public record NotificationUser(Long id, String username, String locale) {
  public String localeTag() {
    return locale == null ? "ko" : locale;
  }
}
