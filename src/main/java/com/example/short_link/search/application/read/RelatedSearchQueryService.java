package com.example.short_link.search.application.read;

import com.example.short_link.note.application.read.NoteSearchTerms;
import com.example.short_link.search.domain.repository.RelatedTagReader;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RelatedSearchQueryService {

  static final int MAX_LIMIT = 20;
  static final int SCAN = 200;

  private final RelatedTagReader tags;

  @Transactional(readOnly = true)
  public RelatedSearchView related(String query, int limit) {
    String trimmed = query == null ? "" : query.strip();
    if (trimmed.isEmpty() || trimmed.length() > NoteSearchTerms.MAX_LENGTH) {
      return RelatedSearchView.NONE;
    }
    NoteSearchTerms terms = NoteSearchTerms.of(trimmed);
    if (terms.match() == null) {
      return RelatedSearchView.NONE;
    }
    return new RelatedSearchView(
        tags.cooccurring(terms.match(), words(trimmed), SCAN, Math.clamp(limit, 1, MAX_LIMIT)));
  }

  static List<String> words(String query) {
    return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[\\s#]+"))
        .filter(word -> !word.isEmpty())
        .distinct()
        .toList();
  }
}
