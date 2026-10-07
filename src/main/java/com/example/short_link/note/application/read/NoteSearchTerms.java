package com.example.short_link.note.application.read;

import java.util.Locale;
import java.util.Set;

// The ngram index holds two-character tokens and drops MySQL's default stopwords, so a query whose
// every term is shorter or made only of stopword bigrams cannot match through it and falls back to
// LIKE. Boolean operators are removed so a query never changes the search's syntax.
public record NoteSearchTerms(String match, String like) {

  public static final int MAX_LENGTH = 100;

  private static final Set<String> STOPWORD_BIGRAMS =
      Set.of(
          "an", "as", "at", "be", "by", "de", "en", "in", "is", "it", "la", "of", "on", "or", "to");

  public static NoteSearchTerms of(String query) {
    String scrubbed = query.replaceAll("[+\\-><()~*\"@]", " ").replaceAll("\\s+", " ").strip();
    for (String term : scrubbed.split(" ")) {
      if (indexed(term)) {
        return new NoteSearchTerms(scrubbed, null);
      }
    }
    String escaped =
        query.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return new NoteSearchTerms(null, "%" + escaped + "%");
  }

  private static boolean indexed(String term) {
    String lower = term.toLowerCase(Locale.ROOT);
    for (int i = 0; i + 2 <= lower.length(); i++) {
      String bigram = lower.substring(i, i + 2);
      if (bigram.indexOf('a') < 0
          && bigram.indexOf('i') < 0
          && !STOPWORD_BIGRAMS.contains(bigram)) {
        return true;
      }
    }
    return false;
  }
}
