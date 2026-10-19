package com.example.short_link.note.application.read;

import java.util.Locale;

// The ngram index holds two-character tokens, so a term shorter than that cannot match through it,
// and a two-letter Latin term (AI, Go) would match inside every longer word; a query with no other
// term falls back to LIKE. Boolean operators and a hashtag's # are removed so a query never changes
// the search's syntax, and every indexed term is required: a bare +term misses a term of one
// bigram,
// +(term) does not.
public record NoteSearchTerms(String match, String like) {

  public static final int MAX_LENGTH = 100;

  public static NoteSearchTerms of(String query) {
    String scrubbed = query.replaceAll("[+\\-><()~*\"@#]", " ").replaceAll("\\s+", " ").strip();
    StringBuilder match = new StringBuilder();
    for (String term : scrubbed.split(" ")) {
      if (indexed(term)) {
        match.append(match.isEmpty() ? "" : " ").append("+(").append(term).append(')');
      }
    }
    if (!match.isEmpty()) {
      return new NoteSearchTerms(match.toString(), null);
    }
    String escaped =
        query.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return new NoteSearchTerms(null, "%" + escaped + "%");
  }

  private static boolean indexed(String term) {
    if (term.length() != 2) {
      return term.length() > 2;
    }
    String lower = term.toLowerCase(Locale.ROOT);
    return !(asciiAlnum(lower.charAt(0)) && asciiAlnum(lower.charAt(1)));
  }

  private static boolean asciiAlnum(char c) {
    return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
  }
}
