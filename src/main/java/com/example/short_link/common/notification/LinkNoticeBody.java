package com.example.short_link.common.notification;

public sealed interface LinkNoticeBody {

  record Written(String text) implements LinkNoticeBody {}

  // Rendered in the owner's language with the single argument filled in.
  record Localized(String messageKey, String argument) implements LinkNoticeBody {}
}
