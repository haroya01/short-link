package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.repository.PostRepository.BlogLanguages;
import jakarta.persistence.Query;

final class BlogLanguageSql {

  private BlogLanguageSql() {}

  static String predicate(BlogLanguages languages) {
    if (languages.tags().isEmpty()) {
      return "";
    }
    return " AND p.language_tag " + (languages.excluded() ? "NOT IN" : "IN") + " (:languages)";
  }

  static Query bind(Query query, BlogLanguages languages) {
    return languages.tags().isEmpty() ? query : query.setParameter("languages", languages.tags());
  }
}
