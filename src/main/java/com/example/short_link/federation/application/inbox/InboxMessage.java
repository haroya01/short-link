package com.example.short_link.federation.application.inbox;

import java.util.Locale;
import java.util.Map;

public record InboxMessage(String path, String query, Map<String, String> headers, byte[] body) {

  public String header(String name) {
    return headers.get(name.toLowerCase(Locale.ROOT));
  }
}
