package com.example.short_link.notification.domain.policy;

import java.util.regex.Pattern;

// Matches the way the iOS and web clients do, so a notice the app hides is the notice the server
// keeps quiet: case-insensitive, and a whole-word phrase may not touch a letter, digit or
// underscore.
public record KeywordFilter(String phrase, boolean wholeWord, boolean hides) {

  public boolean matches(String text) {
    String quoted = Pattern.quote(phrase);
    String source = wholeWord ? "(?<![\\p{L}\\p{N}_])" + quoted + "(?![\\p{L}\\p{N}_])" : quoted;
    return Pattern.compile(source, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
        .matcher(text)
        .find();
  }
}
