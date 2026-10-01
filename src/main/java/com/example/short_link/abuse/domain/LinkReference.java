package com.example.short_link.abuse.domain;

import java.util.Optional;
import java.util.regex.Pattern;

// Reporters paste whatever they received: a bare code, kurl.me/abc123, or a full URL with a query.
public final class LinkReference {

  private static final Pattern SHORT_CODE = Pattern.compile("[0-9A-Za-z]{3,16}");

  private LinkReference() {}

  public static Optional<String> shortCodeOf(String input) {
    if (input == null) {
      return Optional.empty();
    }
    String rest = input.trim();
    int scheme = rest.indexOf("://");
    if (scheme >= 0) {
      rest = rest.substring(scheme + 3);
    }
    int slash = rest.indexOf('/');
    String path = slash >= 0 ? rest.substring(slash + 1) : rest;
    int end = path.length();
    for (char stop : new char[] {'/', '?', '#', '+'}) {
      int at = path.indexOf(stop);
      if (at >= 0 && at < end) {
        end = at;
      }
    }
    String code = path.substring(0, end);
    return SHORT_CODE.matcher(code).matches() ? Optional.of(code) : Optional.empty();
  }
}
