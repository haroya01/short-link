package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.repository.PostRepository.BlogLanguage;
import jakarta.persistence.Query;

final class BlogLanguageSql {

  private BlogLanguageSql() {}

  static String predicate(BlogLanguage language) {
    if (language.tag() == null) {
      return "";
    }
    return " AND p.language_tag " + (language.excluded() ? "<>" : "=") + " :language";
  }

  static Query bind(Query query, BlogLanguage language) {
    return language.tag() == null ? query : query.setParameter("language", language.tag());
  }
}
